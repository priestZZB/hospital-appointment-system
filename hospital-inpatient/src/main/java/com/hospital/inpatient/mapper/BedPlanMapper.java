package com.hospital.inpatient.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 排床优化 Mapper（迭代15 B1-5，注解式）。
 * 统一返回 Map（列 AS "camelCase"）。
 */
@Mapper
public interface BedPlanMapper {

    @Insert("INSERT INTO bed_assign_plan (plan_no, plan_date, suggestions, matched_cnt, unmatched_cnt) " +
            "VALUES (#{planNo}, TO_DATE(#{planDate}, 'YYYY-MM-DD'), #{suggestions}, #{matchedCnt}, #{unmatchedCnt})")
    int insertPlan(@Param("planNo") String planNo, @Param("planDate") String planDate,
                   @Param("suggestions") String suggestions, @Param("matchedCnt") int matchedCnt,
                   @Param("unmatchedCnt") int unmatchedCnt);

    @Select("SELECT id AS \"id\", plan_no AS \"planNo\", TO_CHAR(plan_date, 'YYYY-MM-DD') AS \"planDate\", " +
            "  DBMS_LOB.SUBSTR(suggestions, 2000, 1) AS \"suggestions\", matched_cnt AS \"matchedCnt\", " +
            "  unmatched_cnt AS \"unmatchedCnt\", TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS \"createTime\" " +
            "FROM bed_assign_plan ORDER BY create_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY")
    List<Map<String, Object>> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM bed_assign_plan")
    long countPage();

    /** 空闲床位（按房间号+床号就近排序；跨库不关联科室名） */
    @Select("<script>SELECT b.id AS \"bedId\", b.department_id AS \"departmentId\", " +
            "  b.room_no AS \"roomNo\", b.bed_no AS \"bedNo\", b.bed_type AS \"bedType\", b.daily_fee AS \"dailyFee\" " +
            "FROM bed b WHERE b.status = 'AVAILABLE'" +
            "<if test='departmentId != null'> AND b.department_id = #{departmentId}</if>" +
            " ORDER BY b.room_no, b.bed_no</script>")
    List<Map<String, Object>> availableBeds(@Param("departmentId") Long departmentId);
}
