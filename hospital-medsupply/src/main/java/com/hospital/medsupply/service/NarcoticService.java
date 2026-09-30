package com.hospital.medsupply.service;

import com.hospital.medsupply.entity.NarcoticRegister;
import com.hospital.medsupply.mapper.NarcoticRegisterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 麻精药品五专登记服务
 * <p>
 * 麻醉药品/第一类/第二类精神药品的入出存全留痕流水（五专之专账），
 * 由采购入库、发药、退药、报损等业务内部调用，balance 保持结存连续。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarcoticService {

    /** 入库类动作：结存增加 */
    private static final List<String> IN_ACTIONS = List.of("INBOUND", "RETURN");

    private final NarcoticRegisterMapper narcoticRegisterMapper;

    /**
     * 写入登记流水（balance = 该药当前结存 ± quantity）
     * <p>
     * INBOUND/RETURN 为入（+），OUTBOUND/SCRAP 为出（−）。
     */
    @Transactional(rollbackFor = Exception.class)
    public NarcoticRegister register(Long drugId, String action, int quantity,
                                     Long prescriptionId, Long patientId,
                                     Long operatorId, String remark) {
        NarcoticRegister latest = narcoticRegisterMapper.selectLatestByDrugId(drugId);
        int base = latest != null && latest.getBalance() != null ? latest.getBalance() : 0;
        int balance = IN_ACTIONS.contains(action) ? base + quantity : base - quantity;

        NarcoticRegister register = new NarcoticRegister();
        register.setDrugId(drugId);
        register.setPrescriptionId(prescriptionId);
        register.setPatientId(patientId);
        register.setAction(action);
        register.setQuantity(quantity);
        register.setBalance(balance);
        register.setOperatorId(operatorId);
        register.setRemark(remark);
        narcoticRegisterMapper.insert(register);
        log.info("[麻精五专] 登记: drugId={}, action={}, quantity={}, balance={}",
                drugId, action, quantity, balance);
        return register;
    }

    /**
     * 五专登记册查询（按药品，最新流水在前）
     */
    public List<NarcoticRegister> listByDrugId(Long drugId) {
        return narcoticRegisterMapper.selectByDrugId(drugId);
    }
}
