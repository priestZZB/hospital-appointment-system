package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 病历详情 VO
 */
@Data
@Builder
public class MedicalRecordVO {

    private Long id;
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private Long departmentId;
    private String chiefComplaint;
    private String presentIllness;
    private String pastHistory;
    private BigDecimal temperature;
    private Integer pulse;
    private Integer respiration;
    private String bloodPressure;
    private String diagnosisCode;
    private String diagnosisDesc;
    private String treatmentOpinion;
    private Long referralDeptId;
    private String referralReason;
    private String status;
    private Integer isReturnVisit;
    private List<PrescriptionVO> prescriptions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
