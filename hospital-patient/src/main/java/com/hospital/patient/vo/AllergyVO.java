package com.hospital.patient.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 过敏史 VO
 */
@Data
@Builder
public class AllergyVO {
    private Long id;
    private String allergen;
    private String reactionType;
    private String severity;
    private String source;
    private LocalDateTime createTime;
}
