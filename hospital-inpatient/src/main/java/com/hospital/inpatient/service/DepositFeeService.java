package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.DepositPayDTO;
import com.hospital.inpatient.dto.FeeCreateDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.Bed;
import com.hospital.inpatient.entity.Deposit;
import com.hospital.inpatient.entity.InpatientFee;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.BedOccupancyMapper;
import com.hospital.inpatient.mapper.DepositMapper;
import com.hospital.inpatient.mapper.InpatientFeeMapper;
import com.hospital.inpatient.vo.DailyBillVO;
import com.hospital.inpatient.vo.DepositVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预交金与住院费用服务：
 * 预交金缴纳（余额累加留痕）→ 费用登记（床位/诊疗/药品/检查检验）→ 每日费用清单。
 * 床位费日结幂等（每天每住院记录一条），供出院结算「多退少补」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositFeeService {

    private final DepositMapper depositMapper;
    private final InpatientFeeMapper feeMapper;
    private final AdmissionMapper admissionMapper;
    private final BedOccupancyMapper bedOccupancyMapper;
    private final InpatientSupport support;

    /** 预交金缴纳（患者本人或收费员） */
    @Transactional(rollbackFor = Exception.class)
    public DepositVO payDeposit(DepositPayDTO dto, Long operatorUserId) {
        Admission admission = requireAdmission(dto.getAdmissionId());
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "缴纳金额必须大于 0");
        }
        BigDecimal balance = InpatientSupport.nz(depositMapper.sumByAdmission(admission.getId()))
                .add(dto.getAmount());
        Deposit deposit = new Deposit();
        deposit.setAdmissionId(admission.getId());
        deposit.setAmount(dto.getAmount());
        deposit.setPayMethod(dto.getPayMethod() == null ? "CASH" : dto.getPayMethod());
        deposit.setBalanceAfter(balance);
        deposit.setOperatorId(operatorUserId);
        depositMapper.insert(deposit);
        log.info("[住院预交金] 缴纳: admissionId={}, amount={}, balanceAfter={}",
                admission.getId(), dto.getAmount(), balance);
        return toVO(deposit);
    }

    /** 预交金流水 */
    public List<DepositVO> deposits(Long admissionId) {
        requireAdmission(admissionId);
        return depositMapper.selectByAdmission(admissionId).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    /** 费用登记（收费员/护士手工入账） */
    @Transactional(rollbackFor = Exception.class)
    public InpatientFee postFee(FeeCreateDTO dto) {
        requireAdmission(dto.getAdmissionId());
        InpatientFee fee = new InpatientFee();
        fee.setAdmissionId(dto.getAdmissionId());
        fee.setFeeType(dto.getFeeType() == null ? "OTHER" : dto.getFeeType());
        fee.setItemName(dto.getItemName());
        fee.setAmount(dto.getAmount());
        fee.setBillDate(LocalDate.now());
        feeMapper.insert(fee);
        log.info("[住院费用] 登记: admissionId={}, type={}, item={}, amount={}",
                dto.getAdmissionId(), fee.getFeeType(), dto.getItemName(), dto.getAmount());
        return fee;
    }

    /** 费用流水 */
    public List<InpatientFee> fees(Long admissionId) {
        requireAdmission(admissionId);
        return feeMapper.selectByAdmission(admissionId);
    }

    /** 每日费用清单（按账单日分组汇总） */
    public List<DailyBillVO> dailyBill(Long admissionId) {
        requireAdmission(admissionId);
        Map<LocalDate, List<InpatientFee>> grouped = feeMapper.selectByAdmission(admissionId).stream()
                .collect(Collectors.groupingBy(InpatientFee::getBillDate, LinkedHashMap::new, Collectors.toList()));
        List<DailyBillVO> bills = new ArrayList<>();
        for (Map.Entry<LocalDate, List<InpatientFee>> e : grouped.entrySet()) {
            DailyBillVO bill = new DailyBillVO();
            bill.setBillDate(e.getKey());
            bill.setItems(e.getValue());
            bill.setTotal(e.getValue().stream()
                    .map(InpatientFee::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            bills.add(bill);
        }
        return bills;
    }

    /**
     * 床位费日结（幂等）：为所有在院且占床的住院记录按床位日费计一条 BED 费用。
     * 由管理员手动触发或定时任务调用；同一天同一住院记录不重复计费。
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateDailyBedFees(LocalDate billDate) {
        LocalDate date = billDate == null ? LocalDate.now() : billDate;
        List<Admission> actives = admissionMapper.selectList(null, null, null, "ADMITTED", 0, 1000);
        int count = 0;
        for (Admission admission : actives) {
            if (feeMapper.countBedFeeOnDate(admission.getId(), date) > 0) {
                continue;
            }
            Bed bed = bedOccupancyMapper.selectCurrentBed(admission.getId());
            if (bed == null) {
                continue;
            }
            InpatientFee fee = new InpatientFee();
            fee.setAdmissionId(admission.getId());
            fee.setFeeType("BED");
            fee.setItemName("床位费 " + bed.getRoomNo() + "房" + bed.getBedNo() + "床");
            fee.setAmount(bed.getDailyFee() == null ? BigDecimal.ZERO : bed.getDailyFee());
            fee.setBillDate(date);
            feeMapper.insert(fee);
            count++;
        }
        log.info("[住院费用] 床位费日结: date={}, 计费 {} 条", date, count);
        return count;
    }

    private Admission requireAdmission(Long admissionId) {
        Admission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        support.checkReadable(admission);
        return admission;
    }

    private DepositVO toVO(Deposit d) {
        DepositVO vo = new DepositVO();
        vo.setId(d.getId());
        vo.setAdmissionId(d.getAdmissionId());
        vo.setAmount(d.getAmount());
        vo.setPayMethod(d.getPayMethod());
        vo.setBalanceAfter(d.getBalanceAfter());
        vo.setOperatorId(d.getOperatorId());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }
}
