package com.hospital.clinic.service;

import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.FollowUpPlan;
import com.hospital.clinic.entity.FollowUpRecord;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.FollowUpPlanMapper;
import com.hospital.clinic.mapper.FollowUpRecordMapper;
import com.hospital.clinic.vo.FollowUpPlanVO;
import com.hospital.clinic.vo.FollowUpRecordVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 随访服务：医生创建随访计划 → 回访记录 → 完成/取消
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpPlanMapper planMapper;
    private final FollowUpRecordMapper recordMapper;
    private final DoctorMapper doctorMapper;
    private final PatientFeignClient patientFeignClient;

    @Transactional(rollbackFor = Exception.class)
    public FollowUpPlanVO createPlan(Long userId, FollowUpPlan plan) {
        Doctor doctor = requireDoctor(userId);
        if (plan.getPatientId() == null || plan.getFollowDate() == null || plan.getFollowMethod() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "患者、随访日期与方式不能为空");
        }
        plan.setDoctorId(doctor.getId());
        plan.setStatus("PENDING");
        planMapper.insert(plan);
        log.info("[随访] 创建计划: planId={}, patientId={}, date={}", plan.getId(), plan.getPatientId(), plan.getFollowDate());
        return getPlan(plan.getId());
    }

    public FollowUpPlanVO getPlan(Long planId) {
        FollowUpPlanVO vo = planMapper.selectList(null, null, null)
                .stream().filter(v -> v.getId().equals(planId)).findFirst().orElse(null);
        if (vo == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "随访计划不存在");
        }
        List<FollowUpRecordVO> records = recordMapper.selectByPlanId(planId);
        vo.setRecords(records);
        return vo;
    }

    public List<FollowUpPlanVO> listPlans(Long userId, Long patientId, String status) {
        Long doctorId = null;
        if (UserContext.isPatient()) {
            patientId = resolvePatientId(userId);
        } else if (!UserContext.isAdminOrSuperAdmin()) {
            Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor != null) {
                doctorId = doctor.getId();
            }
        }
        List<FollowUpPlanVO> list = planMapper.selectList(doctorId, patientId, status);
        list.forEach(v -> v.setRecords(recordMapper.selectByPlanId(v.getId())));
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void addRecord(Long userId, Long planId, String content, LocalDate nextFollowDate) {
        FollowUpPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "随访计划不存在");
        }
        Doctor doctor = requireDoctor(userId);
        if (!UserContext.isAdminOrSuperAdmin() && !Objects.equals(plan.getDoctorId(), doctor.getId())) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅随访医生可填写记录");
        }
        FollowUpRecord record = new FollowUpRecord();
        record.setPlanId(planId);
        record.setPatientId(plan.getPatientId());
        record.setDoctorId(doctor.getId());
        record.setContent(content);
        record.setNextFollowDate(nextFollowDate);
        recordMapper.insert(record);
        // 计划置为已完成；如填了下次随访日期，自动生成一条新的待随访计划
        planMapper.updateStatus(planId, "DONE");
        if (nextFollowDate != null) {
            FollowUpPlan next = new FollowUpPlan();
            next.setPatientId(plan.getPatientId());
            next.setMedicalRecordId(plan.getMedicalRecordId());
            next.setDoctorId(doctor.getId());
            next.setFollowDate(nextFollowDate);
            next.setFollowMethod(plan.getFollowMethod());
            next.setTemplate(plan.getTemplate());
            next.setStatus("PENDING");
            planMapper.insert(next);
            log.info("[随访] 生成下次随访: planId={}", next.getId());
        }
        log.info("[随访] 回访记录: planId={}, contentLength={}", planId, content == null ? 0 : content.length());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelPlan(Long userId, Long planId) {
        FollowUpPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "随访计划不存在");
        }
        Doctor doctor = doctorMapper.selectByUserId(userId);
        boolean admin = UserContext.isAdminOrSuperAdmin();
        if (!admin && (doctor == null || !Objects.equals(plan.getDoctorId(), doctor.getId()))) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权取消该随访计划");
        }
        planMapper.updateStatus(planId, "CANCELLED");
        log.info("[随访] 取消计划: planId={}", planId);
    }

    private Doctor requireDoctor(Long userId) {
        Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        return doctor;
    }

    private Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> info = patientFeignClient.getByUserId(userId);
            if (info == null || info.get("id") == null) {
                return null;
            }
            return Long.valueOf(info.get("id").toString());
        } catch (Exception e) {
            return null;
        }
    }
}
