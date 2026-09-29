package com.hospital.clinic.mapper;

import com.hospital.clinic.entity.MedicalCertificate;
import com.hospital.clinic.vo.MedicalCertificateVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 医疗证明 Mapper（注解式） */
@Mapper
public interface MedicalCertificateMapper {

    @Insert("INSERT INTO medical_certificate (cert_no, cert_type, patient_id, doctor_id, medical_record_id, content, days, start_date, status, create_time, update_time) " +
            "VALUES (#{certNo}, #{certType}, #{patientId}, #{doctorId}, #{medicalRecordId}, #{content}, #{days}, #{startDate}, 'ISSUED', SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MedicalCertificate certificate);

    @Select("SELECT id, cert_no, cert_type, patient_id, doctor_id, medical_record_id, content, days, start_date, status, create_time, update_time " +
            "FROM medical_certificate WHERE id = #{id}")
    MedicalCertificate selectById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT c.id, c.cert_no, c.cert_type, c.patient_id, c.doctor_id, c.medical_record_id, c.content, c.days, c.start_date, c.status, c.create_time, c.update_time, " +
            "d.name AS doctor_name " +
            "FROM medical_certificate c LEFT JOIN doctor d ON c.doctor_id = d.id " +
            "WHERE 1=1 " +
            "<if test='doctorId != null'> AND c.doctor_id = #{doctorId} </if>" +
            "<if test='patientId != null'> AND c.patient_id = #{patientId} </if>" +
            "<if test='certType != null and certType != \"\"'> AND c.cert_type = #{certType} </if>" +
            "ORDER BY c.create_time DESC" +
            "</script>")
    List<MedicalCertificateVO> selectList(@Param("doctorId") Long doctorId,
                                          @Param("patientId") Long patientId,
                                          @Param("certType") String certType);

    @Update("UPDATE medical_certificate SET status = 'CANCELLED', update_time = SYSDATE WHERE id = #{id} AND status='ISSUED'")
    int cancel(@Param("id") Long id);
}
