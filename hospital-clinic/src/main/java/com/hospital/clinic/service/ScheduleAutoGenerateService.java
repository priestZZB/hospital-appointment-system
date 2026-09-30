package com.hospital.clinic.service;

import com.hospital.clinic.dto.ScheduleCreateDTO;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.ScheduleGenerateLog;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.ScheduleGenerateLogMapper;
import com.hospital.clinic.mapper.ScheduleMapper;
import com.hospital.clinic.vo.ScheduleVO;
import com.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 自动排班生成服务（迭代9 A8）
 * <p>
 * 默认出诊模板（无模板表，采用简化策略）：
 * <ul>
 *   <li>对象：全院 {@code status=1}（在职）医生，按医生所属科室生成；</li>
 *   <li>时段：AM 08:00 - 12:00，slotDuration=10 分钟 → 24 个号源；</li>
 *   <li>费用：registerFee=20，feeType=NORMAL（普通号）；</li>
 *   <li>幂等：已存在同日同医生排班（任意状态/时段）则跳过该医生；</li>
 *   <li>流程：创建排班（PENDING）→ confirm（置 CONFIRMED 并生成号源）；</li>
 *   <li>留痕：每次执行写 {@code schedule_generate_log}。</li>
 * </ul>
 * 每日凌晨 1 点由 {@link ScheduleGenerateJob} 生成未来第 7 天排班，
 * 亦支持管理端 {@code POST /api/admin/schedule/generate?date=} 手动指定日期触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAutoGenerateService {

    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleService scheduleService;
    private final ScheduleGenerateLogMapper scheduleGenerateLogMapper;

    /** 模板：号别 */
    private static final String TEMPLATE_FEE_TYPE = "NORMAL";
    /** 模板：挂号费 */
    private static final java.math.BigDecimal TEMPLATE_REGISTER_FEE = new java.math.BigDecimal("20");
    /** 模板：单号源时长（分钟） */
    private static final int TEMPLATE_SLOT_DURATION = 10;

    /**
     * 为指定出诊日期生成排班与号源（幂等，可重复执行）。
     *
     * @param bizDate 目标出诊日期（必须不早于今天）
     * @return 执行日志（含新建数量与结果说明，已持久化）
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduleGenerateLog generateForDate(LocalDate bizDate) {
        if (bizDate.isBefore(LocalDate.now())) {
            throw new BusinessException(com.hospital.common.exception.ErrorCodeEnum.PARAM_ERROR,
                    "生成日期不能早于今天: " + bizDate);
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        StringBuilder failReasons = new StringBuilder();

        for (Doctor doctor : doctorMapper.selectByStatus(1)) {
            // 幂等：同日同医生已有排班（不论时段/状态）则跳过
            if (scheduleMapper.selectByDoctorDatePeriod(doctor.getId(), bizDate, "AM") != null) {
                skipped++;
                continue;
            }
            try {
                ScheduleCreateDTO dto = new ScheduleCreateDTO();
                dto.setDoctorId(doctor.getId());
                dto.setDepartmentId(doctor.getDepartmentId());
                dto.setScheduleDate(bizDate);
                dto.setPeriod("AM");
                dto.setPeriodStart(LocalTime.of(8, 0));
                dto.setPeriodEnd(LocalTime.of(12, 0));
                // totalSlots 不传：按 (12:00-08:00)/10min 自动计算为 24
                dto.setSlotDuration(TEMPLATE_SLOT_DURATION);
                dto.setRegisterFee(TEMPLATE_REGISTER_FEE);
                dto.setFeeType(TEMPLATE_FEE_TYPE);
                dto.setOverbook(0);

                // 复用既有排班创建（校验医生/科室/重复）→ confirm（置 CONFIRMED 并生成号源）
                ScheduleVO vo = scheduleService.create(dto);
                scheduleService.confirm(vo.getId());
                created++;
            } catch (BusinessException e) {
                // 单个医生失败（科室停用、并发重复等）不中断整体生成
                failed++;
                if (failReasons.length() < 300) {
                    failReasons.append(doctor.getName()).append(":").append(e.getMessage()).append("; ");
                }
                log.warn("[自动排班] 医生排班生成失败（跳过）: doctorId={}, date={}, error={}",
                        doctor.getId(), bizDate, e.getMessage());
            }
        }

        String message = String.format("自动排班生成：新增 %d 条排班，跳过 %d 条（已存在），失败 %d 条。%s",
                created, skipped, failed, failReasons);

        ScheduleGenerateLog logEntity = new ScheduleGenerateLog();
        logEntity.setBizDate(bizDate);
        logEntity.setCreatedCount(created);
        logEntity.setMessage(message);
        scheduleGenerateLogMapper.insert(logEntity);
        log.info("[自动排班] {}", message);
        return logEntity;
    }
}
