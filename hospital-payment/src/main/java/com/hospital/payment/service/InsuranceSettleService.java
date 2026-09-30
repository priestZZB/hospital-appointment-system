package com.hospital.payment.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.payment.dto.InsuranceSettleDTO;
import com.hospital.payment.entity.InsuranceCatalog;
import com.hospital.payment.entity.InsuranceSettle;
import com.hospital.payment.mapper.InsuranceCatalogMapper;
import com.hospital.payment.mapper.InsuranceSettleMapper;
import com.hospital.payment.vo.InsuranceSettleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医保结算服务（迭代11 H2）。
 * <p>
 * 结算算法（比例以常量写死，detail_json 保留逐项计算过程）：
 * <ul>
 *   <li>逐项查医保目录（{@link #resolveCatalog}，未配置默认自费）：</li>
 *   <li>甲类（A）：统筹支付 = 金额 × 80%，个人 = 金额 × 20%；</li>
 *   <li>乙类（B）：先行自付 15% 直接入个人，剩余 85% 按统筹 80% 支付
 *       （统筹 = 金额 × 85% × 80% = 金额 × 68%，个人 = 金额 × 32%）；</li>
 *   <li>自费（C）：统筹 0，个人全额。</li>
 *   <li>个人部分再拆：个人账户支付 = min(Σ personalPay, 99999)（模拟个账余额充足），
 *       剩余 0 走现金；仅当个人合计超过模拟个账上限才产生现金支付。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceSettleService {

    // ==================== 结算规则常量（毕设模拟口径） ====================

    /** 甲/乙类剩余部分统筹支付比例 80% */
    private static final BigDecimal PLAN_PAY_RATE = new BigDecimal("0.80");

    /** 乙类先行自付比例 15% */
    private static final BigDecimal B_FIRST_SELF_RATE = new BigDecimal("0.15");

    /** 模拟个人账户余额上限：个人合计不超过该值全额按个账支付 */
    private static final BigDecimal PERSONAL_ACCOUNT_LIMIT = new BigDecimal("99999");

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** 结算明细 item_type 合法值 */
    private static final Set<String> ITEM_TYPES =
            Set.of("REGISTER", "DRUG", "EXAM", "LAB", "TREATMENT", "MATERIAL", "CHARGED_ITEM");

    /** 结算单 biz_type 合法值 */
    private static final Set<String> BIZ_TYPES = Set.of("REGISTER", "OUTPATIENT", "INPATIENT");

    private final InsuranceSettleMapper settleMapper;
    private final InsuranceCatalogMapper catalogMapper;

    /**
     * 医保结算：逐项解析目录 → 三色拆分 → 计算统筹/个账/现金 → 写结算单
     *
     * @param dto 结算请求
     * @return 结算单 VO（含三色拆分与逐项明细数组）
     */
    @Transactional(rollbackFor = Exception.class)
    public InsuranceSettleVO settle(InsuranceSettleDTO dto) {
        validate(dto);

        // 1. 逐项拆分
        List<Map<String, Object>> detail = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal catalogA = BigDecimal.ZERO;
        BigDecimal catalogB = BigDecimal.ZERO;
        BigDecimal selfAmount = BigDecimal.ZERO;
        BigDecimal insurancePay = BigDecimal.ZERO;
        BigDecimal personalPay = BigDecimal.ZERO;

        for (int i = 0; i < dto.getItems().size(); i++) {
            InsuranceSettleDTO.SettleItemDTO item = dto.getItems().get(i);
            BigDecimal amount = item.getAmount().setScale(SCALE, ROUNDING);
            String catalogClass = resolveCatalog(item.getItemType(), item.getRefId()).catalogClass();

            BigDecimal itemInsurance;
            BigDecimal itemPersonal;
            BigDecimal firstSelfPay;
            String note;
            switch (catalogClass) {
                case "A" -> {
                    // 甲类：统筹 80%，个人 20%
                    itemInsurance = amount.multiply(PLAN_PAY_RATE).setScale(SCALE, ROUNDING);
                    itemPersonal = amount.subtract(itemInsurance).setScale(SCALE, ROUNDING);
                    firstSelfPay = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
                    note = "甲类: 统筹=金额x80%, 个人=金额x20%";
                }
                case "B" -> {
                    // 乙类：先行自付 15% 入个人，剩余 85% 统筹 80%
                    firstSelfPay = amount.multiply(B_FIRST_SELF_RATE).setScale(SCALE, ROUNDING);
                    BigDecimal rest = amount.subtract(firstSelfPay);
                    itemInsurance = rest.multiply(PLAN_PAY_RATE).setScale(SCALE, ROUNDING);
                    itemPersonal = amount.subtract(itemInsurance).setScale(SCALE, ROUNDING);
                    note = "乙类: 先行自付=金额x15%入个人, 剩余85%统筹x80%, 个人=金额-统筹(含先行自付)";
                }
                default -> {
                    // 自费：统筹 0，个人全额
                    itemInsurance = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
                    itemPersonal = amount;
                    firstSelfPay = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
                    note = "自费: 统筹=0, 个人=全额";
                }
            }

            totalAmount = totalAmount.add(amount);
            switch (catalogClass) {
                case "A" -> catalogA = catalogA.add(amount);
                case "B" -> catalogB = catalogB.add(amount);
                default -> selfAmount = selfAmount.add(amount);
            }
            insurancePay = insurancePay.add(itemInsurance);
            personalPay = personalPay.add(itemPersonal);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemName", displayName(item, catalogClass));
            row.put("itemType", item.getItemType());
            row.put("catalogClass", catalogClass);
            row.put("amount", amount);
            row.put("insurancePay", itemInsurance);
            row.put("personalPay", itemPersonal);
            // 以下为逐项计算过程（审计口径）
            row.put("firstSelfPay", firstSelfPay);
            row.put("note", note);
            detail.add(row);
        }

        // 2. 个人部分再拆：个账支付 = min(personalPay, 99999) 全额按个账（模拟个账余额充足），剩余走现金
        BigDecimal personalAccountPay = personalPay.min(PERSONAL_ACCOUNT_LIMIT).setScale(SCALE, ROUNDING);
        BigDecimal cashAmount = personalPay.subtract(personalAccountPay).setScale(SCALE, ROUNDING);

        // 3. 落库
        InsuranceSettle settle = new InsuranceSettle();
        settle.setSettleNo(settleMapper.nextSettleNo());
        settle.setPatientId(dto.getPatientId());
        settle.setInsuranceNo(dto.getInsuranceNo());
        settle.setBizType(dto.getBizType());
        settle.setBizRefId(dto.getBizRefId());
        settle.setTotalAmount(totalAmount.setScale(SCALE, ROUNDING));
        settle.setCatalogAAmount(catalogA.setScale(SCALE, ROUNDING));
        settle.setCatalogBAmount(catalogB.setScale(SCALE, ROUNDING));
        settle.setSelfAmount(selfAmount.setScale(SCALE, ROUNDING));
        settle.setInsurancePay(insurancePay.setScale(SCALE, ROUNDING));
        settle.setPersonalAccountPay(personalAccountPay);
        settle.setCashAmount(cashAmount);
        settle.setDetailJson(JSONUtil.toJsonStr(detail));
        settle.setStatus("SETTLED");
        settle.setOperatorId(dto.getOperatorId());
        settleMapper.insert(settle);

        log.info("[医保结算] 结算单生成: id={}, settleNo={}, patientId={}, bizType={}, total={}, " +
                        "A={}, B={}, self={}, insurancePay={}, accountPay={}, cash={}",
                settle.getId(), settle.getSettleNo(), settle.getPatientId(), settle.getBizType(),
                settle.getTotalAmount(), settle.getCatalogAAmount(), settle.getCatalogBAmount(),
                settle.getSelfAmount(), settle.getInsurancePay(), settle.getPersonalAccountPay(),
                settle.getCashAmount());

        return toVO(settleMapper.selectById(settle.getId()));
    }

    /**
     * 结算单详情
     */
    public InsuranceSettleVO getDetail(Long id) {
        InsuranceSettle settle = settleMapper.selectById(id);
        if (settle == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "医保结算单不存在");
        }
        return toVO(settle);
    }

    /**
     * 结算单分页
     */
    public Map<String, Object> page(Long patientId, String bizType, Integer pageNo, Integer pageSize) {
        int no = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        int offset = (no - 1) * size;
        long total = settleMapper.countPage(patientId, bizType);
        List<InsuranceSettle> list = settleMapper.selectPage(patientId, bizType, offset, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("pageNo", no);
        result.put("pageSize", size);
        result.put("list", list.stream().map(this::toVO).toList());
        return result;
    }

    /**
     * 冲正：SETTLED → REVERSED（轻量模拟，不回滚支付单/不产生退款流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public InsuranceSettleVO reverse(Long id) {
        int rows = settleMapper.reverse(id);
        if (rows == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "结算单不存在或已冲正，不可重复冲正");
        }
        InsuranceSettle settle = settleMapper.selectById(id);
        log.info("[医保结算] 冲正完成: id={}, settleNo={}, operator={}",
                id, settle.getSettleNo(), settle.getOperatorId());
        return toVO(settle);
    }

    // ==================== 私有方法 ====================

    private void validate(InsuranceSettleDTO dto) {
        if (dto == null || dto.getPatientId() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "缺少患者ID patientId");
        }
        if (dto.getBizType() == null || !BIZ_TYPES.contains(dto.getBizType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "bizType 仅允许 REGISTER/OUTPATIENT/INPATIENT");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "结算明细 items 不能为空");
        }
        for (int i = 0; i < dto.getItems().size(); i++) {
            InsuranceSettleDTO.SettleItemDTO item = dto.getItems().get(i);
            if (item.getItemType() == null || !ITEM_TYPES.contains(item.getItemType())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "第 " + (i + 1) + " 项 itemType 仅允许 REGISTER/DRUG/EXAM/LAB/TREATMENT/MATERIAL/CHARGED_ITEM");
            }
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + (i + 1) + " 项金额必须 >= 0");
            }
        }
    }

    /**
     * 逐项解析医保目录：已配置且启用返回映射；未配置默认自费（C）
     */
    private InsuranceCatalogResolveResult resolveCatalog(String itemType, Long refId) {
        if (refId == null) {
            return InsuranceCatalogResolveResult.unconfigured();
        }
        InsuranceCatalog catalog = catalogMapper.selectByTypeAndRef(itemType, refId);
        if (catalog == null) {
            return InsuranceCatalogResolveResult.unconfigured();
        }
        return new InsuranceCatalogResolveResult(catalog.getCatalogClass(), catalog.getItemName());
    }

    /** 目录解析结果（内部载体） */
    private record InsuranceCatalogResolveResult(String catalogClass, String catalogName) {
        static InsuranceCatalogResolveResult unconfigured() {
            return new InsuranceCatalogResolveResult("C", null);
        }
    }

    /** 明细展示名：请求项名称优先，回退目录名，再回退占位 */
    private String displayName(InsuranceSettleDTO.SettleItemDTO item, String catalogClass) {
        if (item.getItemName() != null && !item.getItemName().isBlank()) {
            return item.getItemName();
        }
        if (item.getRefId() != null) {
            InsuranceCatalog catalog = catalogMapper.selectByTypeAndRef(item.getItemType(), item.getRefId());
            if (catalog != null && catalog.getItemName() != null && !catalog.getItemName().isBlank()) {
                return catalog.getItemName();
            }
        }
        return "未命名项目(" + catalogClass + ")";
    }

    /**
     * 实体 → VO（detail_json 反序列化为明细数组）
     */
    private InsuranceSettleVO toVO(InsuranceSettle settle) {
        List<Map<String, Object>> detail = new ArrayList<>();
        if (settle.getDetailJson() != null && !settle.getDetailJson().isBlank()) {
            JSONArray array = JSONUtil.parseArray(settle.getDetailJson());
            for (Object o : array) {
                if (o instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) map;
                    detail.add(row);
                }
            }
        }
        return InsuranceSettleVO.builder()
                .id(settle.getId())
                .settleNo(settle.getSettleNo())
                .patientId(settle.getPatientId())
                .insuranceNo(settle.getInsuranceNo())
                .bizType(settle.getBizType())
                .bizRefId(settle.getBizRefId())
                .totalAmount(settle.getTotalAmount())
                .catalogAAmount(settle.getCatalogAAmount())
                .catalogBAmount(settle.getCatalogBAmount())
                .selfAmount(settle.getSelfAmount())
                .insurancePay(settle.getInsurancePay())
                .personalAccountPay(settle.getPersonalAccountPay())
                .cashAmount(settle.getCashAmount())
                .status(settle.getStatus())
                .operatorId(settle.getOperatorId())
                .createTime(settle.getCreateTime())
                .detail(detail)
                .build();
    }
}
