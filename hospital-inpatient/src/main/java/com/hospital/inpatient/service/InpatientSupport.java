package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.util.DataScopeUtil;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.Bed;
import com.hospital.inpatient.mapper.BedOccupancyMapper;
import com.hospital.inpatient.mapper.DepositMapper;
import com.hospital.inpatient.mapper.InpatientFeeMapper;
import com.hospital.inpatient.vo.AdmissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 住院模块公共支撑：VO 组装、患者身份解析、姓名批量补齐
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InpatientSupport {

    private final PatientFeignClient patientFeignClient;
    private final BedOccupancyMapper bedOccupancyMapper;
    private final DepositMapper depositMapper;
    private final InpatientFeeMapper feeMapper;

    public Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> info = patientFeignClient.getByUserId(userId);
            if (info == null || info.get("id") == null) {
                return null;
            }
            return Long.valueOf(info.get("id").toString());
        } catch (Exception e) {
            log.warn("[住院] 查询患者档案失败: userId={}", userId, e);
            return null;
        }
    }

    public void fillPatientNames(List<AdmissionVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> ids = list.stream().map(AdmissionVO::getPatientId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> batch = patientFeignClient.getBatch(new ArrayList<>(ids));
            if (batch == null) {
                return;
            }
            Map<Long, String> names = new HashMap<>();
            for (Map<String, Object> info : batch) {
                Object id = info.get("id");
                Object name = info.get("name");
                if (id != null && name != null) {
                    names.put(((Number) id).longValue(), String.valueOf(name));
                }
            }
            list.forEach(v -> v.setPatientName(names.get(v.getPatientId())));
        } catch (Exception e) {
            log.warn("[住院] 患者姓名批量查询失败（忽略）: {}", e.getMessage());
        }
    }

    public AdmissionVO toVO(Admission a) {
        AdmissionVO vo = new AdmissionVO();
        vo.setId(a.getId());
        vo.setAdmissionNo(a.getAdmissionNo());
        vo.setPatientId(a.getPatientId());
        vo.setDepartmentId(a.getDepartmentId());
        vo.setAttendingDoctorId(a.getAttendingDoctorId());
        vo.setAttendingDoctorName(a.getAttendingDoctorName());
        vo.setAdmissionDiag(a.getAdmissionDiag());
        vo.setExpectedDays(a.getExpectedDays());
        vo.setAdmissionTime(a.getAdmissionTime());
        vo.setStatus(a.getStatus());
        vo.setCreateTime(a.getCreateTime());
        Bed bed = bedOccupancyMapper.selectCurrentBed(a.getId());
        if (bed != null) {
            vo.setCurrentBedId(bed.getId());
            vo.setCurrentRoomNo(bed.getRoomNo());
            vo.setCurrentBedNo(bed.getBedNo());
        }
        vo.setDepositBalance(nz(depositMapper.sumByAdmission(a.getId())));
        vo.setTotalFee(nz(feeMapper.sumByAdmission(a.getId())));
        return vo;
    }

    /** 读取权限：患者仅本人；管理员/收费员/医护放行（接口层另有权限码控制） */
    public void checkReadable(Admission admission) {
        if (DataScopeUtil.isAllScope()) {
            return;
        }
        if (UserContext.isPatient()) {
            Long myPatientId = resolvePatientId(UserContext.getUserId());
            if (myPatientId == null || !myPatientId.equals(admission.getPatientId())) {
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权查看他人住院信息");
            }
        }
    }
    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
