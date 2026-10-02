package com.hospital.clinic.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 院内公告 Mapper（迭代13 L3，注解式）。
 * 统一返回 Map（列 AS "camelCase"），规避命名 ResultMap 的解析顺序问题。
 */
@Mapper
public interface NoticeMapper {

    String COLS = "id, title, DBMS_LOB.SUBSTR(content, 2000, 1) AS content, notice_type, status, publisher_id, publisher_name, "
            + "TO_CHAR(publish_time, 'YYYY-MM-DD HH24:MI') AS publish_time_text, "
            + "TO_CHAR(create_time, 'YYYY-MM-DD HH24:MI') AS create_time_text";

    @Insert("INSERT INTO notice (title, content, notice_type, publisher_id, publisher_name) " +
            "VALUES (#{title}, #{content}, NVL(#{noticeType}, 'NOTICE'), #{publisherId}, #{publisherName})")
    int insert(@Param("title") String title, @Param("content") String content,
               @Param("noticeType") String noticeType, @Param("publisherId") Long publisherId,
               @Param("publisherName") String publisherName);

    @Select("<script>SELECT " + COLS + " FROM notice " +
            "<where>" +
            "  <if test='noticeType != null'> AND notice_type = #{noticeType}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "  <if test='keyword != null'> AND title LIKE '%'||#{keyword}||'%'</if>" +
            "</where> ORDER BY publish_time DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    List<Map<String, Object>> selectPage(@Param("noticeType") String noticeType, @Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM notice " +
            "<where>" +
            "  <if test='noticeType != null'> AND notice_type = #{noticeType}</if>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "  <if test='keyword != null'> AND title LIKE '%'||#{keyword}||'%'</if>" +
            "</where></script>")
    long countPage(@Param("noticeType") String noticeType, @Param("status") String status,
                   @Param("keyword") String keyword);

    @Update("UPDATE notice SET status = 'OFFLINE', update_time = SYSDATE WHERE id = #{id} AND status = 'PUBLISHED'")
    int offline(@Param("id") Long id);
}
