package com.hospital.common.interceptor;

import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.constant.RoleConstant;

import java.util.Collections;
import java.util.List;

/**
 * 用户上下文（ThreadLocal）
 * <p>
 * 网关 JwtAuthFilter 验签后通过 Header 透传用户信息（X-User-Id / X-User-Roles / X-User-Permissions），
 * AuthInterceptor 解析 Header 后将信息写入本类，
 * Service 层通过本类获取当前登录用户信息。
 * <p>
 * 使用完毕后必须调用 {@link #clear()} 清理，防止内存泄漏。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = ThreadLocal.withInitial(Collections::emptyList);
    private static final ThreadLocal<List<String>> PERMISSIONS = ThreadLocal.withInitial(Collections::emptyList);

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setRoles(List<String> roles) {
        ROLES.set(roles);
    }

    public static List<String> getRoles() {
        return ROLES.get();
    }

    /**
     * 设置当前用户的权限码列表（由 AuthInterceptor 从 X-User-Permissions Header 解析）。
     * <p>
     * 权限码来源：登录时 auth-service 查 role_permission 写入 Redis，
     * Gateway JwtAuthFilter 每请求读取并注入 Header。
     */
    public static void setPermissions(List<String> permissions) {
        PERMISSIONS.set(permissions != null ? permissions : Collections.emptyList());
    }

    public static List<String> getPermissions() {
        return PERMISSIONS.get();
    }

    /**
     * 判断当前用户是否拥有指定权限码。
     * <p>
     * 超级管理员（{@link RoleConstant#SUPER_ADMIN}）或拥有通配码
     * （{@link PermissionConstant#ALL_PERMISSIONS}）时直接放行。
     */
    public static boolean hasPermission(String permCode) {
        if (permCode == null || permCode.isBlank()) {
            return true;
        }
        // 超管通配：无论缓存是否加载，按角色直接放行
        if (isSuperAdmin()) {
            return true;
        }
        List<String> list = PERMISSIONS.get();
        if (list == null || list.isEmpty()) {
            return false;
        }
        if (list.contains(PermissionConstant.ALL_PERMISSIONS)) {
            return true;
        }
        return list.contains(permCode);
    }

    /**
     * 判断当前用户是否拥有任一指定权限码（「或」关系）。
     */
    public static boolean hasAnyPermission(String... permCodes) {
        if (permCodes == null || permCodes.length == 0) {
            return true;
        }
        for (String code : permCodes) {
            if (hasPermission(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前用户是否拥有指定角色
     */
    public static boolean hasRole(String roleCode) {
        List<String> list = ROLES.get();
        return list != null && list.contains(roleCode);
    }

    /**
     * 判断当前用户是否拥有任一指定角色
     */
    public static boolean hasAnyRole(String... roleCodes) {
        if (roleCodes == null) {
            return false;
        }
        List<String> list = ROLES.get();
        if (list == null) {
            return false;
        }
        for (String roleCode : roleCodes) {
            if (list.contains(roleCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为超级管理员（唯一预置的最高权限角色）
     */
    public static boolean isSuperAdmin() {
        return hasRole(RoleConstant.SUPER_ADMIN);
    }

    /**
     * 是否为管理员（不含超级管理员）
     */
    public static boolean isAdmin() {
        return hasRole(RoleConstant.ADMIN);
    }

    /**
     * 是否拥有「管理员及以上」权限（超级管理员或管理员）
     * <p>
     * 业务管理类接口的权限判断统一使用本方法，
     * 超级管理员拥有管理员的一切权限。
     */
    public static boolean isAdminOrSuperAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN);
    }

    /**
     * 是否为医生
     */
    public static boolean isDoctor() {
        return hasRole(RoleConstant.DOCTOR);
    }

    /**
     * 是否拥有「医生及以上」权限（超级管理员/管理员/医生）
     * <p>
     * 诊疗/叫号等医生业务接口的权限判断统一使用本方法，
     * 管理员与超级管理员可代为操作。
     */
    public static boolean isDoctorOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.DOCTOR);
    }

    /**
     * 是否为患者
     */
    public static boolean isPatient() {
        return hasRole(RoleConstant.PATIENT);
    }

    /**
     * 是否为科主任
     */
    public static boolean isDeptChief() {
        return hasRole(RoleConstant.DEPT_CHIEF);
    }

    /**
     * 是否为药师
     */
    public static boolean isPharmacist() {
        return hasRole(RoleConstant.PHARMACIST);
    }

    /**
     * 是否为检查科技师
     */
    public static boolean isExamTech() {
        return hasRole(RoleConstant.EXAM_TECH);
    }

    /**
     * 是否为护士
     */
    public static boolean isNurse() {
        return hasRole(RoleConstant.NURSE);
    }

    /**
     * 是否拥有「科主任及以上」权限（超级管理员/管理员/科主任）
     * <p>
     * 排班上报、停诊初审等科室管理接口统一使用本方法。
     */
    public static boolean isDeptChiefOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.DEPT_CHIEF);
    }

    /**
     * 是否拥有「药师及以上」权限（超级管理员/管理员/药师）
     * <p>
     * 处方审核、发药等药房接口统一使用本方法。
     */
    public static boolean isPharmacistOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.PHARMACIST);
    }

    /**
     * 是否拥有「检查技师及以上」权限（超级管理员/管理员/检查技师）
     * <p>
     * 检查执行登记、报告录入等检查科接口统一使用本方法。
     */
    public static boolean isExamTechOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.EXAM_TECH);
    }

    /**
     * 是否拥有「护士及以上」权限（超级管理员/管理员/护士）
     * <p>
     * 输液执行、输液记录等护士站接口统一使用本方法。
     */
    public static boolean isNurseOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.NURSE);
    }

    /**
     * 是否为收费员
     */
    public static boolean isCashier() {
        return hasRole(RoleConstant.CASHIER);
    }

    /**
     * 是否拥有「收费员及以上」权限（超级管理员/管理员/收费员）
     * <p>
     * 收费、退费、日结等收费处接口统一使用本方法。
     */
    public static boolean isCashierOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.CASHIER);
    }

    /**
     * 是否为检验技师
     */
    public static boolean isLabTech() {
        return hasRole(RoleConstant.LAB_TECH);
    }

    /**
     * 是否拥有「检验技师及以上」权限（超级管理员/管理员/检验技师）
     * <p>
     * 检验执行、检验结果录入等检验科接口统一使用本方法。
     */
    public static boolean isLabTechOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN, RoleConstant.LAB_TECH);
    }

    /**
     * 是否为分诊护士
     */
    public static boolean isTriageNurse() {
        return hasRole(RoleConstant.TRIAGE_NURSE);
    }

    /**
     * 是否拥有「分诊护士及以上」权限（超级管理员/管理员/分诊护士/护士）
     * <p>
     * 分诊、签到登记、排队叫号辅助等分诊台接口统一使用本方法。
     * 门诊护士（{@link RoleConstant#NURSE}）亦可代为分诊。
     */
    public static boolean isTriageNurseOrAdmin() {
        return hasAnyRole(RoleConstant.SUPER_ADMIN, RoleConstant.ADMIN,
                RoleConstant.TRIAGE_NURSE, RoleConstant.NURSE);
    }

    /**
     * 清理 ThreadLocal（必须搭配拦截器 afterCompletion 调用）
     */
    public static void clear() {
        USER_ID.remove();
        ROLES.remove();
        PERMISSIONS.remove();
    }
}
