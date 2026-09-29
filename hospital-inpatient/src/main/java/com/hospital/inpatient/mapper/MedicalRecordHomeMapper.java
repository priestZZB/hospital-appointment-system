package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.MedicalRecordHome;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/** 住院病案首页 Mapper（注解式） */
@Mapper
public interface MedicalRecordHomeMapper {

    @Insert("INSERT INTO medical_record_home (admission_id, patient_id, department_id, doctor_id, " +
            "admission_time, discharge_time, hospital_days, discharge_diag, main_operation, " +
            "fee_bed, fee_drug, fee_exam, fee_lab, fee_other, fee_total, settlement_amount, create_time) " +
            "VALUES (#{admissionId}, #{patientId}, #{departmentId}, #{doctorId}, #{admissionTime}, " +
            "#{dischargeTime}, #{hospitalDays}, #{dischargeDiag}, #{mainOperation}, " +
            "#{feeBed}, #{feeDrug}, #{feeExam}, #{feeLab}, #{feeOther}, #{feeTotal}, #{settlementAmount}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MedicalRecordHome home);

    @Results(id = "homeMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "admission_id", property = "admissionId"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "department_id", property = "departmentId"),
            @Result(column = "doctor_id", property = "doctorId"),
            @Result(column = "admission_time", property = "admissionTime"),
            @Result(column = "discharge_time", property = "dischargeTime"),
            @Result(column = "hospital_days", property = "hospitalDays"),
            @Result(column = "discharge_diag", property = "dischargeDiag"),
            @Result(column = "main_operation", property = "mainOperation"),
            @Result(column = "fee_bed", property = "feeBed"),
            @Result(column = "fee_drug", property = "feeDrug"),
            @Result(column = "fee_exam", property = "feeExam"),
            @Result(column = "fee_lab", property = "feeLab"),
            @Result(column = "fee_other", property = "feeOther"),
            @Result(column = "fee_total", property = "feeTotal"),
            @Result(column = "settlement_amount", property = "settlementAmount"),
            @Result(column = "create_time", property = "createTime")
    })
    @Select("SELECT id, admission_id, patient_id, department_id, doctor_id, admission_time, discharge_time, " +
            "hospital_days, discharge_diag, main_operation, fee_bed, fee_drug, fee_exam, fee_lab, fee_other, " +
            "fee_total, settlement_amount, create_time FROM medical_record_home WHERE admission_id = #{admissionId}")
    MedicalRecordHome selectByAdmission(@Param("admissionId") Long admissionId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM inpatient_fee WHERE admission_id = #{admissionId} AND fee_type = #{feeType}")
    BigDecimal sumFeeByType(@Param("admissionId") Long admissionId, @Param("feeType") String feeType);
}
