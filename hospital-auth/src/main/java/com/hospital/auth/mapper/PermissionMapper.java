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
}
