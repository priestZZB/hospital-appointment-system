package com.hospital.inpatient.mapper;

import com.hospital.inpatient.entity.RecordBorrow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 病案借阅 Mapper（注解式，迭代12 I3） */
@Mapper
public interface RecordBorrowMapper {

    String COLS = "id, record_id, borrower_id, borrower_name, purpose, borrow_time, expect_return_time, return_time, status";

    @Insert("INSERT INTO record_borrow (record_id, borrower_id, borrower_name, purpose, expect_return_time, status, borrow_time) " +
            "VALUES (#{recordId}, #{borrowerId}, #{borrowerName}, #{purpose}, #{expectReturnTime}, 'BORROWED', SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(RecordBorrow borrow);

    @Select("SELECT " + COLS + " FROM record_borrow WHERE id = #{id}")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "record_id", property = "recordId"),
            @Result(column = "borrower_id", property = "borrowerId"),
            @Result(column = "borrower_name", property = "borrowerName"),
            @Result(column = "purpose", property = "purpose"),
            @Result(column = "borrow_time", property = "borrowTime"),
            @Result(column = "expect_return_time", property = "expectReturnTime"),
            @Result(column = "return_time", property = "returnTime"),
            @Result(column = "status", property = "status")
    })
    RecordBorrow selectById(@Param("id") Long id);

    @Select("SELECT " + COLS + " FROM record_borrow WHERE record_id = #{recordId} ORDER BY id DESC")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "record_id", property = "recordId"),
            @Result(column = "borrower_id", property = "borrowerId"),
            @Result(column = "borrower_name", property = "borrowerName"),
            @Result(column = "purpose", property = "purpose"),
            @Result(column = "borrow_time", property = "borrowTime"),
            @Result(column = "expect_return_time", property = "expectReturnTime"),
            @Result(column = "return_time", property = "returnTime"),
            @Result(column = "status", property = "status")
    })
    List<RecordBorrow> selectByRecord(@Param("recordId") Long recordId);

    @Select("<script>SELECT " + COLS + " FROM record_borrow " +
            "<where>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{pageSize} ROWS ONLY</script>")
    @Results(value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "record_id", property = "recordId"),
            @Result(column = "borrower_id", property = "borrowerId"),
            @Result(column = "borrower_name", property = "borrowerName"),
            @Result(column = "purpose", property = "purpose"),
            @Result(column = "borrow_time", property = "borrowTime"),
            @Result(column = "expect_return_time", property = "expectReturnTime"),
            @Result(column = "return_time", property = "returnTime"),
            @Result(column = "status", property = "status")
    })
    List<RecordBorrow> selectPage(@Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) FROM record_borrow " +
            "<where>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "</where></script>")
    long countPage(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM record_borrow WHERE record_id = #{recordId} AND status = 'BORROWED'")
    int countBorrowed(@Param("recordId") Long recordId);

    @Update("UPDATE record_borrow SET status = 'RETURNED', return_time = SYSDATE " +
            "WHERE id = #{id} AND record_id = #{recordId} AND status = 'BORROWED'")
    int returnBack(@Param("id") Long id, @Param("recordId") Long recordId);
}
