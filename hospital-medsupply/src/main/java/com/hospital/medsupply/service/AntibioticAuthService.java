package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.Drug;
import com.hospital.medsupply.entity.DoctorAntibioticAuth;
import com.hospital.medsupply.mapper.DrugMapper;
import com.hospital.medsupply.mapper.DoctorAntibioticAuthMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 抗菌药物分级授权服务
 * <p>
 * 授权序：NON_RESTRICTED &lt; RESTRICTED &lt; SPECIAL。
 * 医生无授权记录时视为仅可使用非限制级（NON_RESTRICTED）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntibioticAuthService {

    private static final Set<String> LEVELS = Set.of("NON_RESTRICTED", "RESTRICTED", "SPECIAL");

    private final DoctorAntibioticAuthMapper authMapper;
    private final DrugMapper drugMapper;

    /**
     * 授权（doctor_id 唯一，重复授权即更新分级）
     */
    @Transactional(rollbackFor = Exception.class)
    public DoctorAntibioticAuth grant(Long doctorId, String maxLevel, Long approverId) {
        if (doctorId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "doctorId 不能为空");
        }
        if (maxLevel == null || !LEVELS.contains(maxLevel)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "maxLevel 仅支持 NON_RESTRICTED / RESTRICTED / SPECIAL");
        }
        authMapper.upsert(doctorId, maxLevel, approverId);
        log.info("[抗菌授权] 授权: doctorId={}, maxLevel={}, approverId={}", doctorId, maxLevel, approverId);
        return authMapper.selectByDoctorId(doctorId);
    }

    /**
     * 撤销授权
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long doctorId) {
        if (authMapper.selectByDoctorId(doctorId) == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "该医生无有效授权记录");
        }
        authMapper.revoke(doctorId);
        log.info("[抗菌授权] 撤销: doctorId={}", doctorId);
    }

    /**
     * 查询医生有效授权
     */
    public DoctorAntibioticAuth getAuth(Long doctorId) {
        return authMapper.selectByDoctorId(doctorId);
    }

    /**
     * 全部授权列表
     */
    public List<DoctorAntibioticAuth> list() {
        return authMapper.selectAll();
    }

    /**
     * 开方抗菌分级校验
     * <p>
     * 对每个 antibiotic_level 非 NULL 的药品比对医生授权序，
     * 超授权返回违规描述；医生无授权记录视为仅 NON_RESTRICTED。
     *
     * @return 违规描述列表（空列表 = 通过）
     */
    public List<String> checkAntibiotic(Long doctorId, List<Long> drugIds) {
        List<String> violations = new ArrayList<>();
        if (doctorId == null || drugIds == null || drugIds.isEmpty()) {
            return violations;
        }
        DoctorAntibioticAuth auth = authMapper.selectByDoctorId(doctorId);
        String grantedLevel = auth != null ? auth.getMaxLevel() : null;
        int grantedRank = levelRank(grantedLevel);

        for (Long drugId : drugIds) {
            Drug drug = drugMapper.selectById(drugId);
            if (drug == null || drug.getAntibioticLevel() == null) {
                continue;
            }
            if (levelRank(drug.getAntibioticLevel()) > grantedRank) {
                violations.add("药品[" + safeName(drug.getDrugName(), String.valueOf(drugId)) + "]为"
                        + levelLabel(drug.getAntibioticLevel()) + "级抗菌药物，超出医生当前授权"
                        + levelLabel(grantedLevel != null ? grantedLevel : "NON_RESTRICTED") + "级");
            }
        }
        if (!violations.isEmpty()) {
            log.info("[抗菌授权] 开方校验命中: doctorId={}, violations={}", doctorId, violations.size());
        }
        return violations;
    }

    /** 授权序：NON_RESTRICTED(0) &lt; RESTRICTED(1) &lt; SPECIAL(2)，未知按最低档 */
    private static int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case "RESTRICTED" -> 1;
            case "SPECIAL" -> 2;
            default -> 0;
        };
    }

    private static String levelLabel(String level) {
        return switch (level == null ? "" : level) {
            case "RESTRICTED" -> "限制使用";
            case "SPECIAL" -> "特殊使用";
            default -> "非限制使用";
        };
    }

    private static String safeName(String name, String fallback) {
        return name != null && !name.isBlank() ? name : fallback;
    }

    /**
     * 从内部契约明细中提取药品 ID 列表
     */
    public static List<Long> extractDrugIds(List<Map<String, Object>> items) {
        List<Long> drugIds = new ArrayList<>();
        if (items == null) {
            return drugIds;
        }
        for (Map<String, Object> item : items) {
            Object drugId = item.get("drugId");
            if (drugId instanceof Number number) {
                drugIds.add(number.longValue());
            } else if (drugId != null) {
                try {
                    drugIds.add(Long.parseLong(drugId.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // 非法明细项跳过
                }
            }
        }
        return drugIds;
    }
}
