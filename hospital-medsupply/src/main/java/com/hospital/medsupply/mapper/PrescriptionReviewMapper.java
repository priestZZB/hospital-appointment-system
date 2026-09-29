package com.hospital.medsupply.mapper;

import com.hospital.medsupply.entity.PrescriptionReview;
import com.hospital.medsupply.vo.PrescriptionReviewVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 处方点评 Mapper（注解式） */
@Mapper
public interface PrescriptionReviewMapper {

    @Insert("INSERT INTO prescription_review (prescription_id, patient_id, pharmacist_id, rating, problem_type, review_comment, create_time) " +
            "VALUES (#{prescriptionId}, #{patientId}, #{pharmacistId}, #{rating}, #{problemType}, #{comment}, SYSDATE)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(PrescriptionReview review);

    @Select("<script>" +
            "SELECT r.id, r.prescription_id, r.patient_id, r.pharmacist_id, r.rating, r.problem_type, r.review_comment AS \"comment\", r.create_time " +
            "FROM prescription_review r " +
            "WHERE 1=1 " +
            "<if test='prescriptionId != null'> AND r.prescription_id = #{prescriptionId} </if>" +
            "<if test='pharmacistId != null'> AND r.pharmacist_id = #{pharmacistId} </if>" +
            "ORDER BY r.create_time DESC" +
            "</script>")
    List<PrescriptionReviewVO> selectList(@Param("prescriptionId") Long prescriptionId,
                                          @Param("pharmacistId") Long pharmacistId);
}
