package com.hospital.auth.service;

import com.hospital.auth.dto.UserPageQueryDTO;
import com.hospital.auth.entity.User;
import com.hospital.auth.mapper.UserMapper;
import com.hospital.auth.vo.UserVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 分页查询用户列表（含 total）
     *
     * @param dto 查询条件 + 分页参数
     * @return 分页结果（records + total）
     */
    public PageResult<UserVO> pageQuery(UserPageQueryDTO dto) {
        List<UserVO> list = userMapper.selectPage(dto);
        long total = userMapper.countPage(dto);
        return new PageResult<>(list, total, dto.getPageNo(), dto.getPageSize());
    }

    /**
     * 变更用户启用/禁用状态
     *
     * @param id     用户 ID
     * @param status 目标状态：1-启用 0-停用
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (status != 1 && status != 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "状态值只能为 0 或 1");
        }
        userMapper.updateStatus(id, status);
        log.info("[用户] 状态变更: userId={}, status={}", id, status);
    }

    /**
     * 分页结果封装（records + total + 分页参数）
     */
    @Data
    @AllArgsConstructor
    public static class PageResult<T> {
        private List<T> records;
        private long total;
        private Integer pageNo;
        private Integer pageSize;
    }
}
