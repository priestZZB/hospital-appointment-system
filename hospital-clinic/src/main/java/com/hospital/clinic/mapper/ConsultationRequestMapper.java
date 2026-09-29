package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.ConsultationRequest;
import com.hospital.clinic.vo.ConsultationRequestVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 会诊请求 Mapper（注解式） */
@Mapper
public interface ConsultationRequestMapper {

    @Insert("INSERT INTO consultation_request (request_no, medical_record_id, patient_id, apply_dept_id, apply_doctor_id, " +
            "target_dept_id, target_doctor_id, reason, status, create_time, update_time) " +
            "VALUES (#{requestNo}, #{medicalRecordId}, #{patientId}, #{applyDeptId}, #{applyDoctorId}, " +
            "#{targetDeptId}, #{targetDoctorId}, #{reason}, #{status}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ConsultationRequest request);

    @Select("SELECT id, request_no, medical_record_id, patient_id, apply_dept_id, apply_doctor_id, " +
            "target_dept_id, target_doctor_id, reason, status, consult_opinion, consult_doctor_id, " +
            "consult_time, create_time, update_time FROM consultation_request WHERE id = #{id}")
    ConsultationRequest selectById(@Param("id") Long id);

    /** 医生/管理员可以查：按目标科室、目标医生、参与医生、患者、状态过滤 */
    @Select("<script>" +
            "SELECT cr.id, cr.request_no, cr.medical_record_id, cr.patient_id, cr.apply_dept_id, cr.apply_doctor_id, " +
            "cr.target_dept_id, cr.target_doctor_id, cr.reason, cr.status, cr.consult_opinion, cr.consult_doctor_id, " +
            "cr.consult_time, cr.create_time, cr.update_time, " +
            "ad.dept_name AS apply_dept_name, td.dept_name AS target_dept_name, " +
            "adoc.name AS apply_doctor_name, tdoc.name AS target_doctor_name, " +
            "doc.name AS consult_doctor_name " +
            "FROM consultation_request cr " +
            "LEFT JOIN department ad ON cr.apply_dept_id = ad.id " +
            "LEFT JOIN department td ON cr.target_dept_id = td.id " +
            "LEFT JOIN doctor adoc ON cr.apply_doctor_id = adoc.id " +
            "LEFT JOIN doctor tdoc ON cr.target_doctor_id = tdoc.id " +
            "LEFT JOIN doctor doc ON cr.consult_doctor_id = doc.id " +
            "WHERE 1=1 " +
            "<if test='targetDeptId != null'> AND cr.target_dept_id = #{targetDeptId} </if>" +
            "<if test='targetDoctorId != null'> AND cr.target_doctor_id = #{targetDoctorId} </if>" +
            "<if test='participantDoctorId != null'> AND (cr.apply_doctor_id = #{participantDoctorId} OR cr.target_doctor_id = #{participantDoctorId}) </if>" +
            "<if test='patientId != null'> AND cr.patient_id = #{patientId} </if>" +
            "<if test='status != null and status != \"\"'> AND cr.status = #{status} </if>" +
            "ORDER BY cr.create_time DESC" +
            "</script>")
    List<ConsultationRequestVO> selectList(@Param("targetDeptId") Long targetDeptId,
                                           @Param("targetDoctorId") Long targetDoctorId,
                                           @Param("participantDoctorId") Long participantDoctorId,
                                           @Param("patientId") Long patientId,
                                           @Param("status") String status);

    @Select("SELECT cr.id, cr.request_no, cr.medical_record_id, cr.patient_id, cr.apply_dept_id, cr.apply_doctor_id, " +
            "cr.target_dept_id, cr.target_doctor_id, cr.reason, cr.status, cr.consult_opinion, cr.consult_doctor_id, " +
            "cr.consult_time, cr.create_time, cr.update_time, " +
            "ad.dept_name AS apply_dept_name, td.dept_name AS target_dept_name, " +
            "adoc.name AS apply_doctor_name, tdoc.name AS target_doctor_name, " +
            "doc.name AS consult_doctor_name " +
            "FROM consultation_request cr " +
            "LEFT JOIN department ad ON cr.apply_dept_id = ad.id " +
            "LEFT JOIN department td ON cr.target_dept_id = td.id " +
            "LEFT JOIN doctor adoc ON cr.apply_doctor_id = adoc.id " +
            "LEFT JOIN doctor tdoc ON cr.target_doctor_id = tdoc.id " +
            "LEFT JOIN doctor doc ON cr.consult_doctor_id = doc.id " +
            "WHERE cr.id = #{id}")
    ConsultationRequestVO selectVOById(@Param("id") Long id);

    @Select("SELECT * FROM consultation_request WHERE patient_id = #{patientId} ORDER BY create_time DESC")
    List<ConsultationRequest> selectByPatient(@Param("patientId") Long patientId);

    @Update("UPDATE consultation_request SET status = #{status}, consult_opinion = #{opinion}, " +
            "consult_doctor_id = #{consultDoctorId}, consult_time = SYSDATE, update_time = SYSDATE " +
            "WHERE id = #{id}")
    int updateHandle(@Param("id") Long id, @Param("status") String status,
                     @Param("opinion") String opinion, @Param("consultDoctorId") Long consultDoctorId);
}


