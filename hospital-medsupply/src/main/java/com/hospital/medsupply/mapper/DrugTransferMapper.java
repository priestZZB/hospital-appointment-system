package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugTransfer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 药品调拨单 Mapper（注解式）
 */
@Mapper
public interface DrugTransferMapper {

    /** 插入调拨单 */
    @Insert("INSERT INTO drug_transfer (transfer_no, drug_id, quantity, from_location, to_location, " +
            "batch_no, operator_id, status, create_time) " +
            "VALUES (#{transferNo}, #{drugId}, #{quantity}, #{fromLocation}, #{toLocation}, " +
            "#{batchNo}, #{operatorId}, #{status}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DrugTransfer transfer);

    /** 分页查询调拨单（支持药品/状态筛选，倒序） */
    @Select("<script>" +
            "SELECT * FROM drug_transfer WHERE 1=1 " +
            "<if test='drugId != null'> AND drug_id = #{drugId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @Results(id = "transferMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "transferNo", column = "transfer_no"),
            @Result(property = "drugId", column = "drug_id"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "fromLocation", column = "from_location"),
            @Result(property = "toLocation", column = "to_location"),
            @Result(property = "batchNo", column = "batch_no"),
            @Result(property = "operatorId", column = "operator_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time")
    })
    List<DrugTransfer> selectPage(@Param("drugId") Long drugId,
                                  @Param("status") String status,
                                  @Param("offset") Integer offset,
                                  @Param("limit") Integer limit);

    /** 分页统计调拨单数量 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM drug_transfer WHERE 1=1 " +
            "<if test='drugId != null'> AND drug_id = #{drugId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "</script>")
    long countPage(@Param("drugId") Long drugId, @Param("status") String status);
}
