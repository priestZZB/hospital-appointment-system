package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.DrugBatch;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 药品批次表 Mapper（注解式）
 */
@Mapper
public interface DrugBatchMapper {

    /** 插入批次记录（useGeneratedKeys 回填主键，Oracle 必须显式 keyColumn） */
    @Insert("INSERT INTO drug_batch (drug_id, batch_no, supplier, quantity, production_date, expiry_date, " +
            "inbound_type, status, operator_id, create_time, update_time) " +
            "VALUES (#{drugId}, #{batchNo}, #{supplier}, #{quantity}, #{productionDate}, #{expiryDate}, " +
            "#{inboundType}, #{status}, #{operatorId}, SYSDATE, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DrugBatch batch);

    /** 根据主键查询 */
    @Select("SELECT * FROM drug_batch WHERE id = #{id}")
    @Results(id = "batchMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "drugId", column = "drug_id"),
            @Result(property = "batchNo", column = "batch_no"),
            @Result(property = "supplier", column = "supplier"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "productionDate", column = "production_date"),
            @Result(property = "expiryDate", column = "expiry_date"),
            @Result(property = "inboundType", column = "inbound_type"),
            @Result(property = "status", column = "status"),
            @Result(property = "operatorId", column = "operator_id"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    DrugBatch selectById(@Param("id") Long id);

    /** 按药品查询批次（最新在前） */
    @Select("SELECT * FROM drug_batch WHERE drug_id = #{drugId} ORDER BY create_time DESC, id DESC")
    @ResultMap("batchMap")
    List<DrugBatch> selectByDrugId(@Param("drugId") Long drugId);

    /** 效期预警：30 天内到期的在库批次 */
    @Select("SELECT * FROM drug_batch WHERE status = 'ACTIVE' AND expiry_date <= SYSDATE + 30 " +
            "ORDER BY expiry_date ASC")
    @ResultMap("batchMap")
    List<DrugBatch> selectExpiring();

    /** 按药品查询最新在库批次（退药回冲定位用） */
    @Select("SELECT * FROM drug_batch WHERE drug_id = #{drugId} AND status = 'ACTIVE' " +
            "ORDER BY create_time DESC, id DESC FETCH FIRST 1 ROWS ONLY")
    @ResultMap("batchMap")
    DrugBatch selectLatestActiveByDrugId(@Param("drugId") Long drugId);

    /**
     * 批次扣减（乐观条件：仅在库且余量充足）
     * <p>
     * quantity 扣减至 0 时自动置为 EXHAUSTED。
     *
     * @return 受影响行数，0 表示批次不可用或余量不足
     */
    @Update("UPDATE drug_batch SET quantity = quantity - #{qty}, " +
            "status = CASE WHEN quantity - #{qty} <= 0 THEN 'EXHAUSTED' ELSE status END, " +
            "update_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'ACTIVE' AND quantity >= #{qty}")
    int deduct(@Param("id") Long id, @Param("qty") Integer qty);

    /** 批次回冲（退药入库冲正） */
    @Update("UPDATE drug_batch SET quantity = quantity + #{qty}, update_time = SYSDATE WHERE id = #{id}")
    int increase(@Param("id") Long id, @Param("qty") Integer qty);

    /** 报损：余量清零并标注 SCRAPPED（仅 ACTIVE 批次可报损） */
    @Update("UPDATE drug_batch SET quantity = 0, status = 'SCRAPPED', update_time = SYSDATE " +
            "WHERE id = #{id} AND status = 'ACTIVE'")
    int scrap(@Param("id") Long id);

    /** 分页查询批次（支持药品/状态筛选，倒序） */
    @Select("<script>" +
            "SELECT * FROM drug_batch WHERE 1=1 " +
            "<if test='drugId != null'> AND drug_id = #{drugId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "ORDER BY create_time DESC, id DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY" +
            "</script>")
    @ResultMap("batchMap")
    List<DrugBatch> selectPage(@Param("drugId") Long drugId,
                               @Param("status") String status,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    /** 分页统计批次数量 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM drug_batch WHERE 1=1 " +
            "<if test='drugId != null'> AND drug_id = #{drugId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "</script>")
    long countPage(@Param("drugId") Long drugId, @Param("status") String status);
}
