package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.PrescriptionReview;
import com.hospital.medsupply.mapper.PrescriptionReviewMapper;
import com.hospital.medsupply.vo.PrescriptionReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 处方点评服务：药师对处方进行点评（合理/不合理、问题分类）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionReviewService {

    private final PrescriptionReviewMapper reviewMapper;

    @Transactional(rollbackFor = Exception.class)
    public PrescriptionReviewVO create(Long pharmacistId, PrescriptionReview review) {
        if (review.getPrescriptionId() == null || review.getPatientId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "处方与患者不能为空");
        }
        if (review.getRating() == null
                || !("REASONABLE".equals(review.getRating()) || "UNREASONABLE".equals(review.getRating()))) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "评价仅支持 REASONABLE / UNREASONABLE");
        }
        review.setPharmacistId(pharmacistId);
        reviewMapper.insert(review);
        log.info("[处方点评] 创建: reviewId={}, prescriptionId={}, rating={}",
                review.getId(), review.getPrescriptionId(), review.getRating());
        return list(review.getPrescriptionId(), null).stream()
                .filter(v -> v.getId().equals(review.getId())).findFirst().orElse(null);
    }

    public List<PrescriptionReviewVO> list(Long prescriptionId, Long pharmacistId) {
        return reviewMapper.selectList(prescriptionId, pharmacistId);
    }
}
