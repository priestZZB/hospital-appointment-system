package com.hospital.auth.mapper;

import com.hospital.auth.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限表 Mapper
 */
@Mapper
public interface PermissionMapper {

    /**
     * 根据角色 ID 列表查询权限（联表 role_permission）
     */
    List<Permission> findByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据用户 ID 查询权限（user_role → role_permission → permission）
     *
     * @param userId 用户 ID
     * @return 去重后的启用权限列表
     */
    List<Permission> findByUserId(@Param("userId") Long userId);
}
