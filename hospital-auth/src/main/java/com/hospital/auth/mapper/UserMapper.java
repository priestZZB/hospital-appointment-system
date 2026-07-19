package com.hospital.auth.mapper;

import com.hospital.auth.dto.UserPageQueryDTO;
import com.hospital.auth.entity.User;
import com.hospital.auth.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 根据手机号查询未删除用户
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * 根据主键查询
     */
    User selectById(@Param("id") Long id);

    /**
     * 插入用户，自动回填主键
     */
    int insert(User user);

    /**
     * 分页查询用户列表（联表查角色）
     */
    List<UserVO> selectPage(UserPageQueryDTO dto);

    /**
     * 分页查询总数
     */
    long countPage(UserPageQueryDTO dto);

    /**
     * 更新用户状态（启用/禁用）
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新最后登录时间和 IP
     */
    int updateLastLogin(@Param("id") Long id,
                        @Param("lastLoginIp") String lastLoginIp);
}
