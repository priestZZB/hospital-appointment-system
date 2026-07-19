package com.hospital.auth.mapper;

import com.hospital.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色表 Mapper
 */
@Mapper
public interface RoleMapper {

    /**
     * 查询全部角色
     */
    List<Role> selectAll();

    /**
     * 根据主键查询
     */
    Role selectById(@Param("id") Long id);

    /**
     * 根据角色编码查询
     */
    Role selectByCode(@Param("roleCode") String roleCode);

    /**
     * 查询用户拥有的角色列表（联表 user_role）
     */
    List<Role> findByUserId(@Param("userId") Long userId);

    /**
     * 插入角色，自动回填主键
     */
    int insert(Role role);

    /**
     * 更新角色
     */
    int update(Role role);

    /**
     * 删除角色
     */
    int deleteById(@Param("id") Long id);

    /**
     * 为用户分配角色（插入 user_role）
     */
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 移除用户的某个角色
     */
    int deleteUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 检查用户是否已拥有某角色
     */
    int countUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 删除指定角色的所有用户关联（删除角色时清理）
     */
    int deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除指定角色的所有权限关联（删除角色时清理）
     */
    int deleteRolePermissionsByRoleId(@Param("roleId") Long roleId);
}
