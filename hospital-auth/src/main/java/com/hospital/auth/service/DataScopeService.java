package com.hospital.auth.service;

import com.hospital.auth.dto.DataScopeApplyDTO;
import com.hospital.auth.dto.DataScopeApproveDTO;
import com.hospital.auth.dto.DataScopeApplyQueryDTO;
import com.hospital.auth.entity.DataScopeApply;
import com.hospital.auth.entity.User;
import com.hospital.auth.mapper.DataScopeApplyMapper;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.auth.vo.DataScopeApplyVO;
import com.hospital.common.constant.RoleConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 跨科室数据范围申请服务（迭代 5 阶段 3）
 * <p>
 * 默认数据范围：超管/管理员=全量、收费员/分诊护士=全院、科主任=本科室、
 * 医师=本人病历/处方、影像/检验技师=本科室申请单、护士=本输液室、患者=本人。
 * <p>
 * 跨科室授权：用户提交申请 → 科主任/管理员/超管审批 → APPROVED 行 = 临时/长期授权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataScopeApplyMapper dataScopeApplyMapper;
    private final UserMapper userMapper;

    /**
     * 提交跨科室申请（登录用户）
     */
    @Transactional(rollbackFor = Exception.class)
    public DataScopeApplyVO apply(DataScopeApplyDTO dto) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        DataScopeApply apply = new DataScopeApply();
        apply.setUserId(userId);
        apply.setUserName(user.getRealName());
        apply.setTargetDepartmentId(dto.getTargetDepartmentId());
        apply.setTargetDepartmentName(dto.getTargetDepartmentName());
        apply.setReason(dto.getReason());
        dataScopeApplyMapper.insert(apply);
        log.info("[数据范围] 提交跨科室申请: userId={}, deptId={}", userId, dto.getTargetDepartmentId());
        return toVO(apply);
    }

    /**
     * 审批跨科室申请（科主任/管理员/超管）
     */
    @Transactional(rollbackFor = Exception.class)
    public DataScopeApplyVO approve(Long applyId, DataScopeApproveDTO dto) {
        // 审批权限：科主任/管理员/超管
        if (!UserContext.isDeptChiefOrAdmin() && !UserContext.isSuperAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅科主任/管理员/超管可审批跨科室申请");
        }

        DataScopeApply apply = dataScopeApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "申请不存在: id=" + applyId);
        }
        if (!"PENDING".equals(apply.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该申请已审批，不可重复操作");
        }

        boolean approve = "APPROVE".equalsIgnoreCase(dto.getAction());
        apply.setStatus(approve ? "APPROVED" : "REJECTED");
        apply.setApproverId(UserContext.getUserId());
        apply.setApproverName(resolveApproverName());
        apply.setApproveComment(dto.getApproveComment());
        if (approve && dto.getExpireTime() != null && !dto.getExpireTime().isBlank()) {
            try {
                apply.setExpireTime(LocalDateTime.parse(dto.getExpireTime(), DTF));
            } catch (Exception e) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "过期时间格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }
        dataScopeApplyMapper.updateApprove(apply);
        log.info("[数据范围] 审批跨科室申请: applyId={}, action={}, approverId={}",
                applyId, dto.getAction(), UserContext.getUserId());
        return toVO(apply);
    }

    /**
     * 分页查询（userId 为空 = 查全部，用于待审批列表）
     */
    public UserService.PageResult<DataScopeApplyVO> pageQuery(DataScopeApplyQueryDTO dto) {
        List<DataScopeApplyVO> list = dataScopeApplyMapper.selectPage(dto);
        long total = dataScopeApplyMapper.countPage(dto);
        return new UserService.PageResult<>(list, total, dto.getPageNo(), dto.getPageSize());
    }

    /**
     * 查询用户对某科室是否有效授权（服务间复用）
     */
    public boolean hasActiveApply(Long userId, Long departmentId) {
        if (userId == null || departmentId == null) {
            return false;
        }
        return !dataScopeApplyMapper.selectActiveByUserAndDept(userId, departmentId).isEmpty();
    }

    private String resolveApproverName() {
        User approver = userMapper.selectById(UserContext.getUserId());
        return approver != null ? approver.getRealName() : null;
    }

    private DataScopeApplyVO toVO(DataScopeApply a) {
        DataScopeApplyVO vo = new DataScopeApplyVO();
        vo.setId(a.getId());
        vo.setUserId(a.getUserId());
        vo.setUserName(a.getUserName());
        vo.setTargetDepartmentId(a.getTargetDepartmentId());
        vo.setTargetDepartmentName(a.getTargetDepartmentName());
        vo.setReason(a.getReason());
        vo.setStatus(a.getStatus());
        vo.setApproverId(a.getApproverId());
        vo.setApproverName(a.getApproverName());
        vo.setApproveComment(a.getApproveComment());
        vo.setExpireTime(a.getExpireTime());
        vo.setApplyTime(a.getApplyTime());
        vo.setApproveTime(a.getApproveTime());
        return vo;
    }
}