package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.ResultItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 检验结果明细表 Mapper（注解式）
 * <p>
 * 批插采用 Oracle INSERT ... SELECT ... FROM dual UNION ALL 模式
 * （与 clinic PrescriptionItemMapper.xml 的批插写法一致，注解版用 &lt;script&gt;+&lt;foreach&gt; 展开）。
 */
@Mapper
public interface ResultItemMapper {

    /**
     * 批量插入结果明细（多行 INSERT ... SELECT ... FROM dual UNION ALL）
     * <p>
     * Oracle 多行批插不支持 useGeneratedKeys 回填，覆盖式录入场景无需回填主键。
     */
    @Insert("<script>" +
            "INSERT INTO result_item (report_id, item_code, item_name, result_value, " +
            "unit, ref_range, abnormal_flag, sort_order, create_time) " +
            "SELECT t.report_id, t.item_code, t.item_name, t.result_value, " +
            "t.unit, t.ref_range, t.abnormal_flag, t.sort_order, SYSDATE FROM ( " +
            "<foreach collection='items' item='item' separator=' UNION ALL '>" +
            "SELECT #{item.reportId} AS report_id, #{item.itemCode} AS item_code, " +
            "#{item.itemName} AS item_name, #{item.resultValue} AS result_value, " +
            "#{item.unit} AS unit, #{item.refRange} AS ref_range, " +
            "#{item.abnormalFlag} AS abnormal_flag, #{item.sortOrder} AS sort_order FROM dual" +
            "</foreach>" +
            " ) t" +
            "</script>")
    int insertBatch(@Param("items") List<ResultItem> items);

    /** 按报告查询结果明细（按排序号升序，同序按主键） */
    @Select("SELECT * FROM result_item WHERE report_id = #{reportId} " +
            "ORDER BY sort_order ASC, id ASC")
    @Results(id = "resultItemMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "reportId", column = "report_id"),
            @Result(property = "itemCode", column = "item_code"),
            @Result(property = "itemName", column = "item_name"),
            @Result(property = "resultValue", column = "result_value"),
            @Result(property = "unit", column = "unit"),
            @Result(property = "refRange", column = "ref_range"),
            @Result(property = "abnormalFlag", column = "abnormal_flag"),
            @Result(property = "sortOrder", column = "sort_order"),
            @Result(property = "createTime", column = "create_time")
    })
    List<ResultItem> selectByReportId(@Param("reportId") Long reportId);

    /** 删除报告下全部结果明细（覆盖式录入前清空） */
    @Delete("DELETE FROM result_item WHERE report_id = #{reportId}")
    int deleteByReportId(@Param("reportId") Long reportId);

    /**
     * 回写 exam_report.report_result 行摘要（保持老接口/老页面兼容）。
     * <p>
     * exam_report 实体与 ExamReportMapper 归影像段代理维护，此处仅追加一条独立
     * UPDATE 语句，不改动任何既有文件。
     */
    @Update("UPDATE exam_report SET report_result = #{reportResult}, operator_id = #{operatorId} " +
            "WHERE id = #{reportId}")
    int updateReportResult(@Param("reportId") Long reportId,
                           @Param("reportResult") String reportResult,
                           @Param("operatorId") Long operatorId);
}
