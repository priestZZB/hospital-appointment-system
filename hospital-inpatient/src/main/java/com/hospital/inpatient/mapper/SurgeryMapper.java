package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.Surgery;
import com.hospital.inpatient.vo.SurgeryBoardVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一手术单 Mapper（注解式，迭代10 F1~F5）。
 * <p>
 * 状态机落库动作均带前置状态条件（乐观更新），affected rows = 0 即状态不匹配。
 */
@Mapper
public interface SurgeryMapper {

    String COLS = "id, surgery_no, source, patient_id, apply_doctor_id, surgery_name, surgery_type, " +
            "scheduled_time, operating_room, surgeon_id, anesthesia_method, anesthesiologist_id, " +
            "duration_min, status, apply_id, appointment_id, notes, create_time, update_time";

    /** 生成手术单号：SR + yyyyMMdd + 6 位流水（seq_surgery_no，V3 迁移创建） */
    @Select("SELECT 'SR' || TO_CHAR(SYSDATE, 'YYYYMMDD') || LPAD(TO_CHAR(seq_surgery_no.NEXTVAL), 6, '0') FROM dual")
    String nextSurgeryNo();

    @Insert("INSERT INTO surgery (surgery_no, source, patient_id, apply_doctor_id, surgery_name, surgery_type, " +
            "scheduled_time, operating_room, surgeon_id, anesthesia_method, anesthesiologist_id, status, " +
            "apply_id, appointment_id, notes, create_time, update_time) " +
            "VALUES (#{surgeryNo}, #{source}, #{patientId}, #{applyDoctorId}, #{surgeryName}, #{surgeryType}, " +
            "#{scheduledTime}, #{operatingRoom}, #{surgeonId}, #{anesthesiaMethod}, #{anesthesiologistId}, " +
            "NVL(#{status}, 'APPLIED'), #{applyId}, #{appointmentId}, #{notes}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Surgery surgery);

    @Results(id = "surgeryMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "surgery_no", property = "surgeryNo"),
            @Result(column = "source", property = "source"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "apply_doctor_id", property = "applyDoctorId"),
            @Result(column = "surgery_name", property = "surgeryName"),
            @Result(column = "surgery_type", property = "surgeryType"),
            @Result(column = "scheduled_time", property = "scheduledTime"),
            @Result(column = "operating_room", property = "operatingRoom"),
            @Result(column = "surgeon_id", property = "surgeonId"),
            @Result(column = "anesthesia_method", property = "anesthesiaMethod"),
            @Result(column = "anesthesiologist_id", property = "anesthesiologistId"),
            @Result(column = "duration_min", property = "durationMin"),
            @Result(column = "status", property = "status"),
            @Result(column = "apply_id", property = "applyId"),
            @Result(column = "appointment_id", property = "appointmentId"),
            @Result(column = "notes", property = "notes"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    @Select("SELECT " + COLS + " FROM surgery WHERE id = #{id}")
    Surgery selectById(@Param("id") Long id);

    @ResultMap("surgeryMap")
    @Select("SELECT " + COLS + " FROM surgery WHERE apply_id = #{applyId} FETCH FIRST 1 ROWS ONLY")
    Surgery selectByApplyId(@Param("applyId") Long applyId);

    /** 排台看板（当日列表，带术前评估结论；左连接无评估行结论为 NULL） */
    @Results(id = "boardMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "surgery_no", property = "surgeryNo"),
            @Result(column = "surgery_name", property = "surgeryName"),
            @Result(column = "surgery_type", property = "surgeryType"),
            @Result(column = "patient_id", property = "patientId"),
            @Result(column = "source", property = "source"),
            @Result(column = "surgeon_id", property = "surgeonId"),
            @Result(column = "anesthesia_method", property = "anesthesiaMethod"),
            @Result(column = "operating_room", property = "operatingRoom"),
            @Result(column = "scheduled_time", property = "scheduledTime"),
            @Result(column = "status", property = "status"),
            @Result(column = "preop_conclusion", property = "preopConclusion"),
            @Result(column = "asa_grade", property = "asaGrade")
    })
    @Select("<script>SELECT s.id, s.surgery_no, s.surgery_name, s.surgery_type, s.patient_id, s.source, " +
            "s.surgeon_id, s.anesthesia_method, s.operating_room, s.scheduled_time, s.status, " +
            "pa.conclusion AS preop_conclusion, pa.asa_grade " +
            "FROM surgery s LEFT JOIN preop_assessment pa ON pa.surgery_id = s.id " +
            "WHERE s.scheduled_time &gt;= #{start} AND s.scheduled_time &lt; #{end} " +
            "ORDER BY s.scheduled_time, s.id</script>")
    List<SurgeryBoardVO> selectBoard(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @ResultMap("surgeryMap")
    @Select("<script>SELECT " + COLS + " FROM surgery WHERE 1=1 " +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY</script>")
    List<Surgery> selectPage(@Param("status") String status, @Param("source") String source,
                             @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM surgery WHERE 1=1 " +
            "<if test='status != null and status != &quot;&quot;'> AND status = #{status} </if>" +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if></script>")
    long countPage(@Param("status") String status, @Param("source") String source);

    /** 排台：APPLIED → SCHEDULED（同时落排台时间/手术间/主刀/麻醉方式/麻醉医生） */
    @Update("UPDATE surgery SET status = 'SCHEDULED', scheduled_time = #{scheduledTime}, " +
            "operating_room = #{operatingRoom}, surgeon_id = #{surgeonId}, " +
            "anesthesia_method = #{anesthesiaMethod}, anesthesiologist_id = #{anesthesiologistId}, " +
            "update_time = SYSDATE WHERE id = #{id} AND status = 'APPLIED'")
    int schedule(@Param("id") Long id, @Param("scheduledTime") LocalDateTime scheduledTime,
                 @Param("operatingRoom") String operatingRoom, @Param("surgeonId") Long surgeonId,
                 @Param("anesthesiaMethod") String anesthesiaMethod, @Param("anesthesiologistId") Long anesthesiologistId);

    /** 评估通过推进：SCHEDULED → PREOP_PASSED（其余状态不动） */
    @Update("UPDATE surgery SET status = 'PREOP_PASSED', update_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'SCHEDULED'")
    int markPreopPassed(@Param("id") Long id);

    /** 手术开始：SCHEDULED / PREOP_PASSED → IN_OPERATION */
    @Update("UPDATE surgery SET status = 'IN_OPERATION', update_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('SCHEDULED', 'PREOP_PASSED')")
    int markInOperation(@Param("id") Long id);

    /** 手术完成：IN_OPERATION → OPERATED（时长回写主单，空值保留原值） */
    @Update("UPDATE surgery SET status = 'OPERATED', duration_min = NVL(#{durationMin}, duration_min), " +
            "update_time = SYSDATE WHERE id = #{id} AND status = 'IN_OPERATION'")
    int markOperated(@Param("id") Long id, @Param("durationMin") Integer durationMin);

    /** 撤台/取消：APPLIED / SCHEDULED → CANCELLED */
    @Update("UPDATE surgery SET status = 'CANCELLED', update_time = SYSDATE " +
            "WHERE id = #{id} AND status IN ('APPLIED', 'SCHEDULED')")
    int markCancelled(@Param("id") Long id);
}
