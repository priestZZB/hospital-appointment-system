package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.VitalSignDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.VitalSign;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.VitalSignMapper;
import com.hospital.inpatient.vo.VitalSignVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 生命体征服务：护士多时段录入（体温/脉搏/呼吸/血压/血氧）。
 * 留痕 record_time 供阶段B 算法层（病情趋势）使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VitalSignService {

    private final VitalSignMapper vitalSignMapper;
    private final AdmissionMapper admissionMapper;
    private final InpatientSupport support;

    /** 录入体征 */
    @Transactional(rollbackFor = Exception.class)
    public VitalSignVO record(VitalSignDTO dto, Long operatorUserId) {
        requireAdmission(dto.getAdmissionId());
        VitalSign vital = new VitalSign();
        vital.setAdmissionId(dto.getAdmissionId());
        vital.setTemperature(dto.getTemperature());
        vital.setPulse(dto.getPulse());
        vital.setRespiration(dto.getRespiration());
        vital.setBloodPressure(dto.getBloodPressure());
        vital.setBloodOxygen(dto.getBloodOxygen());
        vital.setOperatorId(operatorUserId);
        vitalSignMapper.insert(vital);
        log.info("[住院体征] 录入: admissionId={}, T={}, P={}, R={}, BP={}, SpO2={}",
                dto.getAdmissionId(), dto.getTemperature(), dto.getPulse(), dto.getRespiration(),
                dto.getBloodPressure(), dto.getBloodOxygen());
        return toVO(vital);
    }

    /** 体征列表（最近 N 条，倒序） */
    public List<VitalSignVO> list(Long admissionId, Integer limit) {
        requireAdmission(admissionId);
        int size = (limit == null || limit < 1) ? 30 : Math.min(limit, 100);
        return vitalSignMapper.selectByAdmission(admissionId, size).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    /** 最新一次体征（看板用，供其他 Service 复用） */
    public VitalSignVO latest(Long admissionId) {
        List<VitalSign> rows = vitalSignMapper.selectByAdmission(admissionId, 1);
        return rows.isEmpty() ? null : toVO(rows.get(0));
    }

    private void requireAdmission(Long admissionId) {
        Admission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        support.checkReadable(admission);
    }

    private VitalSignVO toVO(VitalSign v) {
        VitalSignVO vo = new VitalSignVO();
        vo.setId(v.getId());
        vo.setAdmissionId(v.getAdmissionId());
        vo.setTemperature(v.getTemperature());
        vo.setPulse(v.getPulse());
        vo.setRespiration(v.getRespiration());
        vo.setBloodPressure(v.getBloodPressure());
        vo.setBloodOxygen(v.getBloodOxygen());
        vo.setRecordTime(v.getRecordTime());
        return vo;
    }
}
