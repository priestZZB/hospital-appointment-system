package com.hospital.inpatient.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 统一手术单详情聚合 VO（迭代10：手术单 + 术前评估 + 双同意书 + 手术记录 + 麻醉记录）。
 */
@Data
public class SurgeryDetailVO {
    private SurgeryVO surgery;
    /** 术前评估，未评估为 null */
    private PreopAssessmentVO preop;
    /** 手术同意书（含 signed 状态，未签署时 signed=false） */
    private InformedConsentVO consentSurgery;
    /** 麻醉同意书（含 signed 状态，未签署时 signed=false） */
    private InformedConsentVO consentAnesthesia;
    /** 手术记录，未录入为 null */
    private SurgeryRecordVO record;
    /** 麻醉记录，未录入为 null */
    private AnesthesiaRecordVO anesthesia;
    /** 详情加载时间 */
    private LocalDateTime loadTime;
}
