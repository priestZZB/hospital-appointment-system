package com.hospital.inpatient.vo;

import com.hospital.inpatient.entity.InpatientFee;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 住院每日费用清单 VO */
@Data
public class DailyBillVO {
    private LocalDate billDate;
    private List<InpatientFee> items;
    private BigDecimal total;
}
