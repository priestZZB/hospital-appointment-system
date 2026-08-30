package com.hospital.common.constant;

/**
 * 系统角色常量
 * <p>
 * 十一角色权限体系（对齐真实医院门诊业务）：
 * <ul>
 *   <li>{@link #SUPER_ADMIN} 超级管理员 —— 开局预置、唯一、不可新增/删除，拥有最高权限</li>
 *   <li>{@link #ADMIN} 管理员（信息科/门诊办公室）—— 科室/医生/药品/检查项目/用户等基础数据维护</li>
 *   <li>{@link #CASHIER} 收费员（收费处）—— 挂号收费、诊疗收费（代缴费）、退费、日结对账</li>
 *   <li>{@link #DEPT_CHIEF} 科主任 —— 本科室排班审核、停诊初审、本科室医生管理</li>
 *   <li>{@link #DOCTOR} 医生 —— 接诊、叫号、写病历、开处方/检查/检验/输液医嘱</li>
 *   <li>{@link #PHARMACIST} 药师 —— 处方四查十对审核、发药</li>
 *   <li>{@link #EXAM_TECH} 影像技师 —— 放射/超声/内镜/心电检查执行、影像报告录入</li>
 *   <li>{@link #LAB_TECH} 检验技师 —— 检验标本执行、检验结果录入</li>
 *   <li>{@link #TRIAGE_NURSE} 分诊护士 —— 分诊、签到登记、排队叫号辅助</li>
 *   <li>{@link #NURSE} 护士（门诊护士站）—— 输液执行与记录</li>
 *   <li>{@link #PATIENT} 患者 —— 挂号、缴费、就诊、查报告、查输液、AI 分诊、个人档案</li>
 * </ul>
 * <p>
 * 权限层级：SUPER_ADMIN &gt; ADMIN &gt; 各业务角色（DEPT_CHIEF / DOCTOR / CASHIER / PHARMACIST / EXAM_TECH / LAB_TECH / TRIAGE_NURSE / NURSE）&gt; PATIENT
 * <p>
 * 所有业务代码中的角色判断必须引用本常量，禁止硬编码字符串。
 */
public final class RoleConstant {

    private RoleConstant() {
    }

    /** 超级管理员（预置唯一，不可新增/删除） */
    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    /** 管理员（信息科/门诊办公室） */
    public static final String ADMIN = "ROLE_ADMIN";

    /** 收费员（收费处） */
    public static final String CASHIER = "ROLE_CASHIER";

    /** 科主任 */
    public static final String DEPT_CHIEF = "ROLE_DEPT_CHIEF";

    /** 医生 */
    public static final String DOCTOR = "ROLE_DOCTOR";

    /** 药师 */
    public static final String PHARMACIST = "ROLE_PHARMACIST";

    /** 影像技师（放射/超声/内镜/心电检查执行、影像报告录入） */
    public static final String EXAM_TECH = "ROLE_EXAM_TECH";

    /** 检验技师（检验标本执行、检验结果录入） */
    public static final String LAB_TECH = "ROLE_LAB_TECH";

    /** 分诊护士（分诊、签到登记、排队叫号辅助） */
    public static final String TRIAGE_NURSE = "ROLE_TRIAGE_NURSE";

    /** 护士（门诊护士站，输液执行） */
    public static final String NURSE = "ROLE_NURSE";

    /** 患者 */
    public static final String PATIENT = "ROLE_PATIENT";

    /**
     * 内置角色编码集合（不可被增删改的角色）
     */
    public static final String[] BUILT_IN_ROLES = {
            SUPER_ADMIN, ADMIN, CASHIER, DEPT_CHIEF, DOCTOR, PHARMACIST,
            EXAM_TECH, LAB_TECH, TRIAGE_NURSE, NURSE, PATIENT
    };

    /**
     * 判断角色编码是否为内置角色
     */
    public static boolean isBuiltInRole(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        for (String builtIn : BUILT_IN_ROLES) {
            if (builtIn.equals(roleCode)) {
                return true;
            }
        }
        return false;
    }
}
