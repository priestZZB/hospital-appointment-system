package com.hospital.inpatient.service;

import cn.hutool.core.lang.UUID;
import com.hospital.common.constant.RoleConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.util.DataScopeUtil;
import com.hospital.inpatient.dto.AdmissionCreateDTO;
import com.hospital.inpatient.dto.BedAssignDTO;
import com.hospital.inpatient.dto.TransferDeptDTO;
import com.hospital.inpatient.entity.Admission;
import com.hospital.inpatient.entity.Bed;
import com.hospital.inpatient.mapper.AdmissionMapper;
import com.hospital.inpatient.mapper.BedMapper;
import com.hospital.inpatient.mapper.BedOccupancyMapper;
import com.hospital.inpatient.mapper.InpatientMedicalOrderMapper;
import com.hospital.inpatient.vo.AdmissionVO;
import com.hospital.inpatient.vo.BedVO;
import com.hospital.inpatient.vo.InpatientOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 入院登记与床位服务（住院模块核心）
 * <p>
 * 入院登记 -> 分床/转床 -> 床位看板。
 * 医护人员以 auth userId 标识；患者姓名经 PatientFeignClient 批量补齐，避免跨库 JOIN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AdmissionMapper admissionMapper;
    private final BedMapper bedMapper;
    private final BedOccupancyMapper bedOccupancyMapper;
    private final InpatientMedicalOrderMapper orderMapper;
    private final VitalSignService vitalSignService;
    private final InpatientSupport support;

    @Transactional(rollbackFor = Exception.class)
    public AdmissionVO admit(AdmissionCreateDTO dto, Long operatorUserId) {
        Admission admission = new Admission();
        admission.setAdmissionNo("ZY" + LocalDateTime.now().format(NO_FMT)
                + UUID.fastUUID().toString().substring(0, 4).toUpperCase());
        admission.setPatientId(dto.getPatientId());
        admission.setDepartmentId(dto.getDepartmentId());
        admission.setAttendingDoctorId(dto.getAttendingDoctorId() != null
                ? dto.getAttendingDoctorId() : operatorUserId);
        admission.setAttendingDoctorName(dto.getAttendingDoctorName());
        admission.setAdmissionDiag(dto.getAdmissionDiag());
        admission.setExpectedDays(dto.getExpectedDays());
        admission.setStatus("ADMITTED");
        admission.setAdmissionTime(LocalDateTime.now());
        admissionMapper.insert(admission);
        log.info("[住院] 入院登记: id={}, no={}, patientId={}",
                admission.getId(), admission.getAdmissionNo(), admission.getPatientId());
        return support.toVO(admission);
    }

    public AdmissionVO detail(Long admissionId) {
        Admission admission = requireAdmission(admissionId);
        support.checkReadable(admission);
        return support.toVO(admission);
    }

    /**
     * 住院列表（按数据范围过滤）
     * 患者仅本人；管理员/收费员全量；其余角色按科室或本人过滤
     */
    public List<AdmissionVO> list(Long departmentId, Long doctorId, String status,
                                  Integer pageNo, Integer pageSize) {
        Long patientId = null;
        Long deptFilter = departmentId;
        if (UserContext.isPatient()) {
            patientId = support.resolvePatientId(UserContext.getUserId());
            if (patientId == null) {
                return List.of();
            }
            deptFilter = null;
            doctorId = null;
        } else if (!DataScopeUtil.isAllScope() && deptFilter == null && doctorId == null) {
            doctorId = UserContext.getUserId();
        }
        int page = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);
        List<Admission> rows = admissionMapper.selectList(deptFilter, patientId, doctorId, status,
                (page - 1) * size, size);
        List<AdmissionVO> vos = rows.stream().map(support::toVO).collect(Collectors.toList());
        support.fillPatientNames(vos);
        return vos;
    }

    /**
     * 分床 / 转床（乐观占用，防并发抢同一床）
     */
    @Transactional(rollbackFor = Exception.class)
    public AdmissionVO assignBed(BedAssignDTO dto) {
        Admission admission = requireAdmission(dto.getAdmissionId());
        if (!"ADMITTED".equals(admission.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已出院，不可分床");
        }
        Bed bed = bedMapper.selectById(dto.getBedId());
        if (bed == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "床位不存在");
        }
        if (!Objects.equals(bed.getDepartmentId(), admission.getDepartmentId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "床位与收治科室不一致");
        }
        if (bedMapper.occupyIfAvailable(bed.getId()) == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_OPERATION, "该床位已被占用，请另选床位");
        }
        Bed current = bedOccupancyMapper.selectCurrentBed(admission.getId());
        boolean transfer = current != null;
        if (transfer) {
            bedOccupancyMapper.closeCurrent(admission.getId());
            bedMapper.updateStatus(current.getId(), "AVAILABLE");
        }
        bedOccupancyMapper.insert(admission.getId(), bed.getId(), transfer ? "TRANSFER" : "ADMIT");
        log.info("[住院] {}：admissionId={}, bed={}-{}", transfer ? "转床" : "分床",
                admission.getId(), bed.getRoomNo(), bed.getBedNo());
        return support.toVO(admission);
    }

    /**
     * 科室床位看板（含当前占用者住院号）
     */
    public List<BedVO> bedsOf(Long departmentId) {
        List<Bed> beds = bedMapper.selectList(departmentId, null);
        Map<Long, Map<String, Object>> occupants = new HashMap<>();
        for (Map<String, Object> row : bedOccupancyMapper.selectCurrentOccupants(departmentId)) {
            Object bedId = row.get("bedId");
            if (bedId != null) {
                occupants.put(((Number) bedId).longValue(), row);
            }
        }
        List<BedVO> result = new java.util.ArrayList<>();
        for (Bed b : beds) {
            BedVO vo = new BedVO();
            vo.setId(b.getId());
            vo.setDepartmentId(b.getDepartmentId());
            vo.setRoomNo(b.getRoomNo());
            vo.setBedNo(b.getBedNo());
            vo.setBedType(b.getBedType());
            vo.setDailyFee(b.getDailyFee());
            vo.setStatus(b.getStatus());
            Map<String, Object> occ = occupants.get(b.getId());
            if (occ != null) {
                vo.setOccupantAdmissionNo((String) occ.get("admissionNo"));
                Object pid = occ.get("patientId");
                if (pid != null) {
                    vo.setOccupantPatientId(((Number) pid).longValue());
                }
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 转科（E1）：变更收治科室，原床位释放，由新科室护士站重新分床。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdmissionVO transferDept(TransferDeptDTO dto) {
        Admission admission = requireAdmission(dto.getAdmissionId());
        if (!"ADMITTED".equals(admission.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该患者已出院，不可转科");
        }
        if (java.util.Objects.equals(admission.getDepartmentId(), dto.getTargetDeptId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "转入科室与当前科室相同");
        }
        admissionMapper.updateDepartment(admission.getId(), dto.getTargetDeptId());
        Bed current = bedOccupancyMapper.selectCurrentBed(admission.getId());
        if (current != null) {
            bedOccupancyMapper.closeCurrent(admission.getId());
            bedMapper.updateStatus(current.getId(), "AVAILABLE");
        }
        log.info("[住院] 转科: admissionId={}, {} -> {}, 原床位已释放",
                admission.getId(), admission.getDepartmentId(), dto.getTargetDeptId());
        return support.toVO(admissionMapper.selectById(admission.getId()));
    }

    /**
     * 住院总览看板（护士站/医生站）：在院患者 + 待核对/执行中医嘱数 + 预交金/费用 + 最新体征
     */
    public List<InpatientOverviewVO> overview(Long departmentId) {
        List<Admission> rows = admissionMapper.selectList(departmentId, null, null, "ADMITTED", 0, 200);
        List<AdmissionVO> vos = rows.stream().map(support::toVO).collect(Collectors.toList());
        support.fillPatientNames(vos);
        List<InpatientOverviewVO> result = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Admission a = rows.get(i);
            AdmissionVO avo = vos.get(i);
            InpatientOverviewVO vo = new InpatientOverviewVO();
            vo.setAdmissionId(a.getId());
            vo.setAdmissionNo(a.getAdmissionNo());
            vo.setPatientName(avo.getPatientName());
            vo.setRoomNo(avo.getCurrentRoomNo());
            vo.setBedNo(avo.getCurrentBedNo());
            vo.setStatus(a.getStatus());
            vo.setPendingOrderCount(orderMapper.countByStatus(a.getId(), "OPEN"));
            vo.setExecutingOrderCount(orderMapper.countByStatus(a.getId(), "EXECUTING"));
            vo.setDepositBalance(avo.getDepositBalance());
            vo.setTotalFee(avo.getTotalFee());
            vo.setLatestVital(vitalSignService.latest(a.getId()));
            result.add(vo);
        }
        return result;
    }

    Admission requireAdmission(Long admissionId) {
        Admission admission = admissionMapper.selectById(admissionId);
        if (admission == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "住院记录不存在");
        }
        return admission;
    }
}
