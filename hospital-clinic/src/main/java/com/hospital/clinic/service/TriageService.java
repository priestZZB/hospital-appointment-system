package com.hospital.clinic.service;

import com.hospital.clinic.dto.TriageSetPriorityDTO;
import com.hospital.clinic.entity.Checkin;
import com.hospital.clinic.mapper.CheckinMapper;
import com.hospital.clinic.vo.TriageQueueVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 分诊服务（迭代9 A1 分诊优先级 + A6 检查检验完成回诊）
 * <p>
 * 叫号队列 score 规则（Redis ZSet key = {@code queue:dept:{departmentId}}，ZPOPMIN 取 score 最小者）：
 * <pre>
 *   score = priorityRank × 1e13 + 原签到时间戳（毫秒）
 *   priorityRank：priority 0(急诊)→0、1(优先)→1、2(普通)→2
 *   returnFlag = 1（回诊）→ priorityRank 再减 0.5
 * </pre>
 * 取值空间说明：时间戳毫秒当前约 1.7e12，远小于 1e13，因此不同 priorityRank 的
 * score 区间互不重叠（同档内部按签到时间 FIFO）；回诊减 0.5 档使其插在
 * 同优先级普通患者之前、更高优先级患者之后，实现「同档靠前、插队不越级」。
 * {@link CallService#callNext} 使用 ZPOPMIN 按 score 自然生效，无需感知本规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageService {

    private final CheckinMapper checkinMapper;
    private final PatientFeignClient patientFeignClient;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String QUEUE_KEY_PREFIX = "queue:dept:";

    /** 优先级：急诊 */
    public static final int PRIORITY_EMERGENCY = 0;
    /** 优先级：优先 */
    public static final int PRIORITY_PREFERRED = 1;
    /** 优先级：普通 */
    public static final int PRIORITY_NORMAL = 2;

    /**
     * 计算叫号队列 score（与 Redis ZSet 写入值严格一致，供分诊视图排序复用）。
     *
     * @param priority             分诊优先级 0/1/2（null 视为普通 2）
     * @param returnFlag           回诊标记 0/1（null 视为 0）
     * @param checkinEpochMilli    原签到时间戳（毫秒）
     */
    public static double queueScore(Integer priority, Integer returnFlag, long checkinEpochMilli) {
        int rank;
        if (priority != null && priority == PRIORITY_EMERGENCY) {
            rank = 0;           // 急诊最先
        } else if (priority != null && priority == PRIORITY_PREFERRED) {
            rank = 1;           // 优先其次
        } else {
            rank = 2;           // 普通（含 null/非法值兜底）
        }
        double effectiveRank = rank;
        if (returnFlag != null && returnFlag == 1) {
            // 回诊插队：同档普通患者之前、更高优先级之后（不越级）
            effectiveRank -= 0.5;
        }
        return effectiveRank * 1e13 + checkinEpochMilli;
    }

    /**
     * 分诊设置优先级（A1）
     * <p>
     * 更新 checkin.priority / return_flag / triage_nurse_id / triage_time，
     * 并同步调整 Redis ZSet 的 score（ZADD 语义：member 存在则改 score，
     * 不存在则重新入队，可兼容 Redis 数据丢失后的自愈场景）。
     * 等待中的患者才可调整（已叫号/就诊中不在队列内）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TriageQueueVO setPriority(TriageSetPriorityDTO dto, Long operatorUserId) {
        Checkin checkin = checkinMapper.selectById(dto.getCheckinId());
        if (checkin == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "签到记录不存在");
        }
        if (!"WAITING".equals(checkin.getQueueStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "仅等待中的患者可调整分诊优先级，当前状态: " + checkin.getQueueStatus());
        }
        int returnFlag = dto.getReturnFlag() != null ? dto.getReturnFlag() : 0;

        // 1. 落库（triage_time 由 SQL 取 SYSDATE）
        checkinMapper.updateTriage(checkin.getId(), dto.getPriority(), returnFlag, operatorUserId);

        // 2. 同步 Redis 队列 score：score = priorityRank × 1e13 + 原签到时间戳
        String queueKey = QUEUE_KEY_PREFIX + checkin.getDepartmentId();
        double score = queueScore(dto.getPriority(), returnFlag, toEpochMilli(checkin.getCheckinTime()));
        stringRedisTemplate.opsForZSet().add(queueKey, String.valueOf(checkin.getId()), score);

        log.info("[分诊] 优先级已调整: checkinId={}, priority={}, returnFlag={}, score={}",
                checkin.getId(), dto.getPriority(), returnFlag, score);

        return TriageQueueVO.builder()
                .checkinId(checkin.getId())
                .appointmentId(checkin.getAppointmentId())
                .patientId(checkin.getPatientId())
                .departmentId(checkin.getDepartmentId())
                .doctorId(checkin.getDoctorId())
                .queueStatus("WAITING")
                .priority(dto.getPriority())
                .returnFlag(returnFlag)
                .checkinTime(checkin.getCheckinTime())
                .queueScore(score)
                .build();
    }

    /**
     * 分诊台队列视图（A1）
     * <p>
     * 科室 WAITING 患者按 score 升序（与 Redis ZPOPMIN 取号顺序一致），
     * score 在 Java 侧按同一公式重算，避免 Redis 与 DB 数据漂移影响展示。
     */
    public List<TriageQueueVO> triageQueue(Long departmentId) {
        List<TriageQueueVO> list = checkinMapper.selectWaitingWithTriage(departmentId);
        if (list.isEmpty()) {
            return List.of();
        }
        list.forEach(vo -> vo.setQueueScore(
                queueScore(vo.getPriority(), vo.getReturnFlag(), toEpochMilli(vo.getCheckinTime()))));
        list = list.stream()
                .sorted(Comparator.comparingDouble(TriageQueueVO::getQueueScore))
                .collect(Collectors.toList());
        fillPatientNames(list);
        return list;
    }

    /**
     * 检查/检验完成回诊（A6）
     * <p>
     * 校验签到存在且处于「已被叫到/就诊中」语义（CALLED / RE_CALLED / IN_CONSULT），
     * 置 rejoin_time = now、return_flag = 1、queue_status = WAITING，
     * 并按 score 规则重新加入 Redis 队列（回诊在同档普通患者之前插队）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TriageQueueVO rejoin(Long checkinId) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "签到记录不存在");
        }
        String status = checkin.getQueueStatus();
        // 状态词表（checkin.queue_status）：WAITING / CALLED / RE_CALLED / MISSED / IN_CONSULT
        // 「检查检验完成回诊」要求患者已被叫到（含重呼）或正在就诊中；
        // WAITING（本来就在排队）与 MISSED（过号已有 markMissed 重排路径）不接受回诊。
        boolean callable = "CALLED".equals(status) || "RE_CALLED".equals(status) || "IN_CONSULT".equals(status);
        if (!callable) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "仅已叫号/就诊中的患者可执行回诊，当前状态: " + status);
        }

        // 1. 落库：置回 WAITING，rejoin_time=SYSDATE，return_flag=1
        checkinMapper.updateRejoinWithFlag(checkinId, "WAITING");

        // 2. 重新加入 Redis 队列（ZADD 覆盖 score）：回诊 rank 减 0.5 档插队
        String queueKey = QUEUE_KEY_PREFIX + checkin.getDepartmentId();
        double score = queueScore(checkin.getPriority(), 1, toEpochMilli(checkin.getCheckinTime()));
        stringRedisTemplate.opsForZSet().add(queueKey, String.valueOf(checkinId), score);

        log.info("[回诊] 患者已重新排队: checkinId={}, score={}", checkinId, score);

        return TriageQueueVO.builder()
                .checkinId(checkinId)
                .appointmentId(checkin.getAppointmentId())
                .patientId(checkin.getPatientId())
                .departmentId(checkin.getDepartmentId())
                .doctorId(checkin.getDoctorId())
                .queueStatus("WAITING")
                .priority(checkin.getPriority())
                .returnFlag(1)
                .checkinTime(checkin.getCheckinTime())
                .queueScore(score)
                .build();
    }

    // ==================== 私有方法 ====================

    private void fillPatientNames(List<TriageQueueVO> list) {
        List<Long> ids = list.stream()
                .map(TriageQueueVO::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        try {
            Map<Long, String> names = new HashMap<>();
            for (Map<String, Object> info : patientFeignClient.getBatch(ids)) {
                Object id = info.get("id");
                Object name = info.get("name");
                if (id != null && name != null) {
                    names.put(((Number) id).longValue(), String.valueOf(name));
                }
            }
            list.forEach(vo -> vo.setPatientName(names.get(vo.getPatientId())));
        } catch (Exception e) {
            log.warn("[分诊] 患者姓名批量查询失败（忽略）: {}", e.getMessage());
        }
    }

    private long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            // 兜底：极老数据无签到时间时退化为当前时间，保持队尾语义
            return System.currentTimeMillis();
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
