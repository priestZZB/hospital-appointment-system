package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 线上图文复诊 Mapper（迭代13 K1，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface OnlineConsultMapper {

    String COLS = "c.id AS \"id\", c.consult_no AS \"consultNo\", c.patient_id AS \"patientId\", "
            + "c.patient_name AS \"patientName\", c.doctor_id AS \"doctorId\", d.name AS \"doctorName\", "
            + "c.medical_record_id AS \"medicalRecordId\", c.chief_complaint AS \"chiefComplaint\", "
            + "c.status AS \"status\", c.prescription_id AS \"prescriptionId\", c.prescription_no AS \"prescriptionNo\", "
            + "TO_CHAR(c.create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\", "
            + "TO_CHAR(c.accept_time, 'YYYY-MM-DD HH24:MI') AS \"acceptTime\", "
            + "TO_CHAR(c.close_time, 'YYYY-MM-DD HH24:MI') AS \"closeTime\"";

    @Insert("INSERT INTO online_consult (consult_no, patient_id, patient_name, doctor_id, medical_record_id, chief_complaint) " +
            "VALUES (#{consultNo}, #{patientId}, #{patientName}, #{doctorId}, #{medicalRecordId}, #{chiefComplaint})")
    int insert(@Param("consultNo") String consultNo,
               @Param("patientId") Long patientId, @Param("patientName") String patientName,
               @Param("doctorId") Long doctorId, @Param("medicalRecordId") Long medicalRecordId,
               @Param("chiefComplaint") String chiefComplaint);

    @Select("SELECT id FROM online_consult WHERE consult_no = #{consultNo}")
    Long selectIdByConsultNo(@Param("consultNo") String consultNo);

    @Select("SELECT " + COLS + " FROM online_consult c LEFT JOIN doctor d ON d.id = c.doctor_id WHERE c.id = #{id}")
    Map<String, Object> selectById(@Param("id") Long id);

    @Select("<script>SELECT " + COLS + " FROM online_consult c LEFT JOIN doctor d ON d.id = c.doctor_id " +
            "<where>" +
            "  <if test='patientId != null'> AND c.patient_id = #{patientId}</if>" +
            "  <if test='doctorId != null'> AND c.doctor_id = #{doctorId}</if>" +
            "  <if test='status != null'> AND c.status = #{status}</if>" +
            "</where> ORDER BY c.create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId,
                                         @Param("status") String status,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM online_consult c " +
            "<where>" +
            "  <if test='patientId != null'> AND c.patient_id = #{patientId}</if>" +
            "  <if test='doctorId != null'> AND c.doctor_id = #{doctorId}</if>" +
            "  <if test='status != null'> AND c.status = #{status}</if>" +
            "</where></script>")
    long countPage(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId,
                   @Param("status") String status);

    @Update("UPDATE online_consult SET status = 'IN_PROGRESS', accept_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'WAITING'")
    int accept(@Param("id") Long id);

    @Update("UPDATE online_consult SET status = 'CLOSED', close_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('WAITING', 'IN_PROGRESS')")
    int close(@Param("id") Long id);

    @Update("UPDATE online_consult SET prescription_id = #{prescriptionId}, prescription_no = #{prescriptionNo} " +
            "WHERE id = #{id}")
    int fillPrescription(@Param("id") Long id, @Param("prescriptionId") Long prescriptionId,
                         @Param("prescriptionNo") String prescriptionNo);

    @Insert("INSERT INTO online_consult_message (consult_id, sender_type, sender_id, sender_name, content) " +
            "VALUES (#{consultId}, #{senderType}, #{senderId}, #{senderName}, #{content})")
    int insertMessage(@Param("consultId") Long consultId, @Param("senderType") String senderType,
                      @Param("senderId") Long senderId, @Param("senderName") String senderName,
                      @Param("content") String content);

    @Select("SELECT id AS \"id\", consult_id AS \"consultId\", sender_type AS \"senderType\", " +
            "  sender_id AS \"senderId\", sender_name AS \"senderName\", content AS \"content\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM online_consult_message WHERE consult_id = #{consultId} ORDER BY id")
    List<Map<String, Object>> selectMessages(@Param("consultId") Long consultId);

    @Select("SELECT id AS \"id\", diagnosis_desc AS \"diagnosisDesc\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD') AS \"createTime\" " +
            "FROM medical_record WHERE patient_id = #{patientId} ORDER BY create_time DESC FETCH FIRST 1 ROWS ONLY")
    Map<String, Object> selectLatestRecord(@Param("patientId") Long patientId);

    @Insert("INSERT INTO prescription (prescription_no, medical_record_id, patient_id, doctor_id, status) " +
            "VALUES (#{prescriptionNo}, #{medicalRecordId}, #{patientId}, #{doctorId}, 'PENDING_REVIEW')")
    int insertPrescription(@Param("prescriptionNo") String prescriptionNo,
                           @Param("medicalRecordId") Long medicalRecordId, @Param("patientId") Long patientId,
                           @Param("doctorId") Long doctorId);

    @Select("SELECT id FROM prescription WHERE prescription_no = #{prescriptionNo}")
    Long selectPrescriptionIdByNo(@Param("prescriptionNo") String prescriptionNo);

    @Insert("INSERT INTO prescription_item (prescription_id, drug_id, drug_name, specification, dosage, " +
            "usage_method, frequency, days, quantity, unit, remark) " +
            "VALUES (#{prescriptionId}, #{drugId}, #{drugName}, #{specification}, #{dosage}, " +
            "#{usageMethod}, #{frequency}, #{days}, #{quantity}, #{unit}, #{remark})")
    int insertPrescriptionItem(@Param("prescriptionId") Long prescriptionId, @Param("drugId") Long drugId,
                               @Param("drugName") String drugName, @Param("specification") String specification,
                               @Param("dosage") String dosage, @Param("usageMethod") String usageMethod,
                               @Param("frequency") String frequency, @Param("days") Integer days,
                               @Param("quantity") Integer quantity, @Param("unit") String unit,
                               @Param("remark") String remark);
}
