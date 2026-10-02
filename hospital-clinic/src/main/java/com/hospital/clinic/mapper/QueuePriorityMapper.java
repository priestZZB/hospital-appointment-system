package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 急诊优先级叫号 Mapper（迭代15 B1-2）与停诊重调度 Mapper（B1-3）。
 * 统一返回 Map（列 AS "camelCase"）。
 */
@Mapper
public interface QueuePriorityMapper {

    /** 当日未完成分诊队列（供优先级评分） */
    @Select("SELECT t.id AS \"triageId\", t.visit_no AS \"visitNo\", t.patient_id AS \"patientId\", " +
            "  t.patient_name AS \"patientName\", t.triage_level AS \"triageLevel\", t.status AS \"status\", " +
            "  ROUND((SYSDATE - CAST(t.create_time AS DATE)) * 1440) AS \"waitMinutes\", " +
            "  TO_CHAR(t.create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM triage_record t WHERE TRUNC(t.create_time) = TRUNC(SYSDATE) AND t.status != 'DONE' " +
            "ORDER BY t.create_time")
    List<Map<String, Object>> todayQueue();

    /** 停诊重调度批次插入 */
    @Insert("INSERT INTO reschedule_batch (batch_no, doctor_id, stop_date, affected_cnt, suggestions) " +
            "VALUES (#{batchNo}, #{doctorId}, TO_DATE(#{stopDate}, 'YYYY-MM-DD'), #{affectedCnt}, #{suggestions})")
    int insertBatch(@Param("batchNo") String batchNo, @Param("doctorId") Long doctorId,
                    @Param("stopDate") String stopDate, @Param("affectedCnt") int affectedCnt,
                    @Param("suggestions") String suggestions);

    @Select("SELECT id AS \"id\", batch_no AS \"batchNo\", doctor_id AS \"doctorId\", " +
            "  TO_CHAR(stop_date, 'YYYY-MM-DD') AS \"stopDate\", affected_cnt AS \"affectedCnt\", " +
            "  DBMS_LOB.SUBSTR(suggestions, 2000, 1) AS \"suggestions\", status AS \"status\", " +
            "  TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM reschedule_batch ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY")
    List<Map<String, Object>> selectBatchPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM reschedule_batch")
    long countBatchPage();

    /** 某医生某天的有效预约（受影响对象） */
    @Select("SELECT a.id AS \"appointmentId\", a.patient_id AS \"patientId\", " +
            "  a.slot_seq AS \"slotSeq\", a.order_status AS \"orderStatus\", " +
            "  TO_CHAR(a.appointment_date, 'YYYY-MM-DD') AS \"scheduleDate\", d.name AS \"doctorName\" " +
            "FROM appointment a " +
            "JOIN doctor d ON d.id = a.doctor_id " +
            "WHERE a.doctor_id = #{doctorId} AND a.appointment_date = TO_DATE(#{stopDate}, 'YYYY-MM-DD') " +
            "AND a.order_status NOT IN ('CANCELLED', 'REFUNDED') ORDER BY a.slot_seq")
    List<Map<String, Object>> affectedAppointments(@Param("doctorId") Long doctorId,
                                                   @Param("stopDate") String stopDate);

    /** 同科室其他可替医生（按当日号源余量倒序） */
    @Select("SELECT d.id AS \"doctorId\", d.name AS \"doctorName\", COUNT(s.id) AS \"slotCnt\" " +
            "FROM doctor d LEFT JOIN schedule s ON s.doctor_id = d.id " +
            "AND s.schedule_date = TO_DATE(#{stopDate}, 'YYYY-MM-DD') " +
            "WHERE d.department_id = #{departmentId} AND d.id != #{excludeDoctorId} AND d.status = 1 " +
            "GROUP BY d.id, d.name ORDER BY COUNT(s.id) DESC")
    List<Map<String, Object>> alternativeDoctors(@Param("departmentId") Long departmentId,
                                                 @Param("excludeDoctorId") Long excludeDoctorId,
                                                 @Param("stopDate") String stopDate);

    /** 医生所属科室 */
    @Select("SELECT department_id FROM doctor WHERE id = #{doctorId}")
    Long doctorDepartment(@Param("doctorId") Long doctorId);

    /** 批次状态更新 */
    @Update("UPDATE reschedule_batch SET status = 'NOTIFIED' WHERE id = #{id} AND status = 'GENERATED'")
    int markNotified(@Param("id") Long id);
}
