package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.Specimen;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.SpecimenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标本采集服务（迭代8 C2）
 * <p>
 * 检验类申请缴费后采集标本并生成标本号条码；核收状态机：
 * COLLECTED → RECEIVED（核收）/ REJECTED（拒收，必须填写原因）；RECEIVED → TESTING（检测中）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecimenService {

    private static final DateTimeFormatter SEQ_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SpecimenMapper specimenMapper;
    private final ExamApplicationMapper examApplicationMapper;

    /**
     * 标本采集登记：校验申请 + 生成标本号 + 插入 COLLECTED 记录
     * <p>
     * 校验规则：申请存在、itemType=LAB（仅检验类可采标本）、已缴费（PAID）、
     * 申请状态允许采集（PENDING / EXECUTING，已完成/已取消不可采）。
     *
     * @param applicationId 检验申请 ID
     * @param specimenType  标本类型（BLOOD/URINE/STOOL/SPUTUM/OTHER，空默认 BLOOD）
     * @param container     容器
     * @param collectSite   采集部位
     * @param operatorId    采集人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Specimen collect(Long applicationId, String specimenType, String container,
                            String collectSite, Long operatorId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "applicationId 不能为空");
        }
        ExamApplication application = examApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.EXAM_APPLICATION_NOT_FOUND);
        }
        if (!"LAB".equals(application.getItemType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅检验类（LAB）项目可采集标本");
        }
        if (!"PAID".equals(application.getPayStatus())) {
            throw new BusinessException(ErrorCodeEnum.PAY_NOT_COMPLETED, "检验申请尚未缴费，不可采集标本");
        }
        if (!"PENDING".equals(application.getStatus()) && !"EXECUTING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "当前申请状态（" + application.getStatus() + "）不允许采集标本");
        }

        Specimen specimen = new Specimen();
        specimen.setSpecimenNo(generateSpecimenNo());
        specimen.setApplicationId(applicationId);
        specimen.setPatientId(application.getPatientId());
        specimen.setSpecimenType(specimenType == null || specimenType.isBlank() ? "BLOOD" : specimenType);
        specimen.setContainer(container);
        specimen.setCollectSite(collectSite);
        specimen.setStatus("COLLECTED");
        specimen.setCollectorId(operatorId);
        specimenMapper.insert(specimen);
        log.info("[标本] 采集登记: specimenId={}, specimenNo={}, applicationId={}, type={}, collectorId={}",
                specimen.getId(), specimen.getSpecimenNo(), applicationId, specimen.getSpecimenType(), operatorId);
        return specimenMapper.selectById(specimen.getId());
    }

    /**
     * 标本核收/拒收
     *
     * @param specimenId 标本 ID
     * @param accept     true-核收 RECEIVED / false-拒收 REJECTED
     * @param remark     拒收原因（拒收时必填）
     * @param operatorId 核收人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Specimen receive(Long specimenId, boolean accept, String remark, Long operatorId) {
        if (specimenId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "specimenId 不能为空");
        }
        Specimen specimen = specimenMapper.selectById(specimenId);
        if (specimen == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "标本不存在");
        }
        if (!"COLLECTED".equals(specimen.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已采集（COLLECTED）状态的标本可核收");
        }
        if (!accept && (remark == null || remark.isBlank())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "拒收标本必须填写拒收原因");
        }
        String targetStatus = accept ? "RECEIVED" : "REJECTED";
        int rows = specimenMapper.updateStatus(specimenId, targetStatus, operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "标本核收失败，状态已变更");
        }
        if (!accept) {
            specimenMapper.updateRemark(specimenId, remark);
        }
        log.info("[标本] {}完成: specimenId={}, specimenNo={}, operatorId={}",
                accept ? "核收" : "拒收", specimenId, specimen.getSpecimenNo(), operatorId);
        return specimenMapper.selectById(specimenId);
    }

    /**
     * 标本送检：RECEIVED → TESTING（检测中）
     */
    @Transactional(rollbackFor = Exception.class)
    public Specimen markTesting(Long specimenId) {
        if (specimenId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "specimenId 不能为空");
        }
        Specimen specimen = specimenMapper.selectById(specimenId);
        if (specimen == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "标本不存在");
        }
        int rows = specimenMapper.markTesting(specimenId);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅核收（RECEIVED）状态的标本可进入检测中");
        }
        log.info("[标本] 送检流转: specimenId={}, specimenNo={}, RECEIVED→TESTING", specimenId, specimen.getSpecimenNo());
        return specimenMapper.selectById(specimenId);
    }

    /**
     * 按主键查询标本
     */
    public Specimen getById(Long specimenId) {
        if (specimenId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "specimenId 不能为空");
        }
        Specimen specimen = specimenMapper.selectById(specimenId);
        if (specimen == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "标本不存在");
        }
        return specimen;
    }

    /**
     * 按检验申请查询标本列表（一单可多管，最新在前）
     */
    public List<Specimen> getByApplication(Long applicationId) {
        return specimenMapper.selectByApplicationId(applicationId);
    }

    /**
     * 标本分页（支持状态/标本类型筛选）
     */
    public Map<String, Object> listByPage(String status, String specimenType, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<Specimen> records = specimenMapper.selectByPage(status, specimenType, offset, pageSize);
        long total = specimenMapper.countPage(status, specimenType);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 生成标本号：SP + yyyyMMdd + 6 位当日序列
     * <p>
     * 序列取「当日同前缀条数 + 1」为基准，逐号探测唯一索引避免并发/删除导致的撞号。
     */
    private String generateSpecimenNo() {
        String prefix = "SP" + LocalDate.now().format(SEQ_DATE);
        int seq = (int) specimenMapper.countByNoPrefix(prefix) + 1;
        int guard = 0;
        while (guard++ < 1000) {
            if (seq > 999999) {
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "当日标本号序列已耗尽");
            }
            String candidate = prefix + String.format("%06d", seq);
            if (specimenMapper.selectByNo(candidate) == null) {
                return candidate;
            }
            seq++;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "标本号生成失败，请稍后重试");
    }
}
