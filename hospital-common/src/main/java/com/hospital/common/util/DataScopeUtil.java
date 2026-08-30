package com.hospital.common.util;

import com.hospital.common.constant.RoleConstant;
import com.hospital.common.interceptor.UserContext;

import java.util.List;

/**
 * 数据范围解析工具（迭代 5 阶段 3）
 * <p>
 * 按当前登录用户角色解析默认数据范围，供各服务数据查询过滤使用。
 * 默认规则（与迭代 5 方案一致）：
 * <ul>
 *   <li>ALL（全量）：超管 / 管理员 —— 管理数据全量；</li>
 *   <li>GLOBAL（全院）：收费员 / 分诊护士 —— 岗位性质跨科室；</li>
 *   <li>DEPT（本科室）：科主任 / 影像技师 / 检验技师 / 护士 —— 本科室数据；</li>
 *   <li>SELF（本人）：医师 —— 本人开的病历/处方；</li>
 *   <li>NONE（仅本人）：患者 —— 仅本人数据。</li>
 * </ul>
 * <p>
 * 跨科室授权走 {@code data_scope_apply} 申请审批（auth 服务），
 * 本工具负责「默认范围」判断；跨科室授权结果由调用方通过
 * {@code DataScopeAuthorizer} 或 auth 内部接口补充判断。
 */
public final class DataScopeUtil {

    /** 数据范围类型 */
    public enum ScopeType {
        /** 全量（超管/管理员） */
        ALL,
        /** 全院（收费员/分诊护士） */
        GLOBAL,
        /** 本科室（科主任/技师/护士） */
        DEPT,
        /** 本人（医师：本人病历/处方） */
        SELF,
        /** 仅本人数据（患者） */
        NONE
    }

    private DataScopeUtil() {
    }

    /**
     * 当前用户的数据范围类型
     */
    public static ScopeType getScopeType() {
        List<String> roles = UserContext.getRoles();
        if (roles == null || roles.isEmpty()) {
            return ScopeType.NONE;
        }
        if (roles.contains(RoleConstant.SUPER_ADMIN) || roles.contains(RoleConstant.ADMIN)) {
            return ScopeType.ALL;
        }
        if (roles.contains(RoleConstant.CASHIER) || roles.contains(RoleConstant.TRIAGE_NURSE)) {
            return ScopeType.GLOBAL;
        }
        if (roles.contains(RoleConstant.DEPT_CHIEF)
                || roles.contains(RoleConstant.EXAM_TECH)
                || roles.contains(RoleConstant.LAB_TECH)
                || roles.contains(RoleConstant.NURSE)) {
            return ScopeType.DEPT;
        }
        if (roles.contains(RoleConstant.DOCTOR) || roles.contains(RoleConstant.PHARMACIST)) {
            return ScopeType.SELF;
        }
        return ScopeType.NONE;
    }

    /**
     * 是否全量数据范围（无需任何过滤）
     */
    public static boolean isAllScope() {
        return getScopeType() == ScopeType.ALL || getScopeType() == ScopeType.GLOBAL;
    }

    /**
     * 是否本科室范围（科主任/技师/护士；需按科室过滤）
     */
    public static boolean isDeptScope() {
        return getScopeType() == ScopeType.DEPT;
    }

    /**
     * 是否本人范围（医师；需按本人过滤）
     */
    public static boolean isSelfScope() {
        return getScopeType() == ScopeType.SELF;
    }
}