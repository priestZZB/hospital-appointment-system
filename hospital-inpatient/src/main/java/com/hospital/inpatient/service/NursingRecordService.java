package com.hospital.inpatient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.inpatient.dto.NursingRecordDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.NursingRecord;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.NursingRecordMapper;
import com.hospital.inpatient.vo.NursingRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 护理病历服务（E2）：护士记录护理记录单与出入量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NursingRecordService {

    private final NursingRecordMapper nursingRecordMapper;
    private final AdmissionMapper admissionMapper;
    private final InpatientSupport support;

    /** 录入护理记录 */
    @Transactional(rollbackFor = Exception.class)
    public NursingRecordVO record(NursingRecordDTO dto, Long nurseUserId) {
        requireAdmission(dto.getAdmissionId());
        NursingRecord record = new NursingRecord();
        record.setAdmissionId(dto.getAdmissionId());
        record.setRecordType(dto.getRecordType() == null ? "ROUTINE" : dto.getRecordType());
        record.setContent(dto.getContent());
        record.setIntakeMl(dto.getIntakeMl());
        record.setOutputMl(dto.getOutputMl());
        record.setNurseId(nurseUserId);
        nursingRecordMapper.insert(record);
        log.info("[护理病历] 录入: admissionId={}, type={}, nurseId={}",
                dto.getAdmissionId(), record.getRecordType(), nurseUserId);
        return toVO(record);
    }

    /** 护理记录列表 */
    public List<NursingRecordVO> list(Long admissionId, String recordType, Integer limit) {
        requireAdmission(admissionId);
        int size = (limit == null || limit < 1) ? 50 : Math.min(limit, 200);
        return nursingRecordMapper.selectByAdmission(admissionId, recordType, size).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    private void requireAdmission(Long admissionId) {
        Admission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        support.checkReadable(admission);
    }

    private NursingRecordVO toVO(NursingRecord r) {
        NursingRecordVO vo = new NursingRecordVO();
        vo.setId(r.getId());
        vo.setAdmissionId(r.getAdmissionId());
        vo.setRecordType(r.getRecordType());
        vo.setContent(r.getContent());
        vo.setIntakeMl(r.getIntakeMl());
        vo.setOutputMl(r.getOutputMl());
        vo.setNurseId(r.getNurseId());
        vo.setRecordTime(r.getRecordTime());
        return vo;
    }
}
