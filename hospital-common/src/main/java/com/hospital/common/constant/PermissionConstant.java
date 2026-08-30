package com.hospital.common.constant;

/**
 * 系统权限码常量（迭代 5 权限底座）。
 * <p>
 * 权限码命名规范：{@code api:模块:动作}（接口）、{@code menu:模块:页}（菜单）、{@code btn:模块:动作}（按钮）。
 * <p>
 * 与 {@code hospital-auth} 下 Flyway 迁移脚本 {@code V7__permission_seed.sql} 的植入数据一一对应，
 * 后端 {@code @RequiresPermission} 注解与前端 {@code v-permission} 指令必须引用本常量，禁止硬编码字符串。
 * <p>
 * 超管（{@link RoleConstant#SUPER_ADMIN}）默认通配全部权限，无需逐条绑定。
 */
public final class PermissionConstant {

    private PermissionConstant() {
    }

    // ==================== 认证与系统管理（auth） ====================

    /** 查询角色列表/详情 */
    public static final String AUTH_ROLE_QUERY = "api:auth:role:query";
    /** 创建角色（仅超管） */
    public static final String AUTH_ROLE_CREATE = "api:auth:role:create";
    /** 编辑角色（仅超管） */
    public static final String AUTH_ROLE_UPDATE = "api:auth:role:update";
    /** 删除角色（仅超管） */
    public static final String AUTH_ROLE_DELETE = "api:auth:role:delete";
    /** 为用户分配角色（仅超管） */
    public static final String AUTH_ROLE_ASSIGN = "api:auth:role:assign";
    /** 用户分页查询 */
    public static final String AUTH_USER_QUERY = "api:auth:user:query";
    /** 创建用户 */
    public static final String AUTH_USER_CREATE = "api:auth:user:create";
    /** 变更用户状态 */
    public static final String AUTH_USER_UPDATE = "api:auth:user:update";
    /** 审计日志查询 */
    public static final String AUTH_AUDIT_QUERY = "api:auth:audit:query";
    /** 查询岗位列表/详情（仅超管） */
    public static final String AUTH_POSITION_LIST = "api:auth:position:list";
    /** 创建岗位（仅超管） */
    public static final String AUTH_POSITION_CREATE = "api:auth:position:create";
    /** 编辑岗位（仅超管） */
    public static final String AUTH_POSITION_UPDATE = "api:auth:position:update";
    /** 删除岗位（仅超管） */
    public static final String AUTH_POSITION_DELETE = "api:auth:position:delete";
    /** 给用户分配岗位（仅超管） */
    public static final String AUTH_POSITION_ASSIGN = "api:auth:position:assign";
    /** 提交跨科室数据范围申请 */
    public static final String AUTH_DATA_SCOPE_APPLY = "api:auth:data-scope:apply";
    /** 审批跨科室数据范围申请（超管/管理员/科主任） */
    public static final String AUTH_DATA_SCOPE_APPROVE = "api:auth:data-scope:approve";
    /** 跨科室数据范围申请列表（本人） */
    public static final String AUTH_DATA_SCOPE_LIST = "api:auth:data-scope:list";

    // ==================== 患者档案（patient） ====================

    /** 查询本人档案 */
    public static final String PATIENT_PROFILE_QUERY = "api:patient:profile:query";
    /** 编辑本人档案 */
    public static final String PATIENT_PROFILE_UPDATE = "api:patient:profile:update";
    /** 就诊卡查询 */
    public static final String PATIENT_VISIT_CARD_QUERY = "api:patient:visit-card:query";
    /** 提交实名认证 */
    public static final String PATIENT_REALNAME_SUBMIT = "api:patient:realname:submit";
    /** 实名认证审核（管理员） */
    public static final String PATIENT_REALNAME_REVIEW = "api:patient:realname:review";
    /** 过敏史查询 */
    public static final String PATIENT_ALLERGY_QUERY = "api:patient:allergy:query";
    /** 新增过敏史 */
    public static final String PATIENT_ALLERGY_CREATE = "api:patient:allergy:create";
    /** 删除过敏史 */
    public static final String PATIENT_ALLERGY_DELETE = "api:patient:allergy:delete";
    /** 文件上传 */
    public static final String PATIENT_FILE_UPLOAD = "api:patient:file:upload";

    // ==================== 核心诊疗（clinic） ====================

    /** 科室列表查询 */
    public static final String CLINIC_DEPT_QUERY = "api:clinic:dept:query";
    /** 新增科室（管理员） */
    public static final String CLINIC_DEPT_CREATE = "api:clinic:dept:create";
    /** 编辑科室（管理员） */
    public static final String CLINIC_DEPT_UPDATE = "api:clinic:dept:update";
    /** 科室状态变更（管理员） */
    public static final String CLINIC_DEPT_STATUS = "api:clinic:dept:status";
    /** 排班提交（医生/科主任） */
    public static final String CLINIC_SCHEDULE_CREATE = "api:clinic:schedule:create";
    /** 排班确认（管理员） */
    public static final String CLINIC_SCHEDULE_CONFIRM = "api:clinic:schedule:confirm";
    /** 排班驳回（管理员） */
    public static final String CLINIC_SCHEDULE_REJECT = "api:clinic:schedule:reject";
    /** 排班取消（管理员） */
    public static final String CLINIC_SCHEDULE_CANCEL = "api:clinic:schedule:cancel";
    /** 号源查询 */
    public static final String CLINIC_SLOT_QUERY = "api:clinic:slot:query";
    /** 挂号下单（患者） */
    public static final String CLINIC_APPOINTMENT_CREATE = "api:clinic:appointment:create";
    /** 我的预约查询 */
    public static final String CLINIC_APPOINTMENT_QUERY = "api:clinic:appointment:query";
    /** 预约全量分页（管理员） */
    public static final String CLINIC_APPOINTMENT_PAGE = "api:clinic:appointment:page";
    /** 取消预约（患者本人） */
    public static final String CLINIC_APPOINTMENT_CANCEL = "api:clinic:appointment:cancel";
    /** 患者签到 */
    public static final String CLINIC_CHECKIN_CREATE = "api:clinic:checkin:create";
    /** 排队状态查询 */
    public static final String CLINIC_CHECKIN_QUERY = "api:clinic:checkin:query";
    /** 排队快照（医生/管理员） */
    public static final String CLINIC_CHECKIN_SNAPSHOT = "api:clinic:checkin:snapshot";
    /** 叫号（医生） */
    public static final String CLINIC_CALL_NEXT = "api:clinic:call:next";
    /** 重呼（医生） */
    public static final String CLINIC_CALL_RECALL = "api:clinic:call:recall";
    /** 过号（医生） */
    public static final String CLINIC_CALL_MISSED = "api:clinic:call:missed";
    /** 开始接诊（医生） */
    public static final String CLINIC_CONSULT_START = "api:clinic:consult:start";
    /** 保存病历（医生） */
    public static final String CLINIC_CONSULT_SAVE = "api:clinic:consult:save";
    /** 病历详情/列表查询 */
    public static final String CLINIC_CONSULT_QUERY = "api:clinic:consult:query";
    /** 患者病历列表 */
    public static final String CLINIC_CONSULT_PATIENT = "api:clinic:consult:patient";
    /** 结束就诊（医生） */
    public static final String CLINIC_CONSULT_FINISH = "api:clinic:consult:finish";
    /** 今日接诊队列（医生/管理员） */
    public static final String CLINIC_CONSULT_TODAY = "api:clinic:consult:today";
    /** 开具处方（医生） */
    public static final String CLINIC_PRESCRIPTION_CREATE = "api:clinic:prescription:create";
    /** 处方查询（未缴费，患者本人/收费员） */
    public static final String CLINIC_PRESCRIPTION_QUERY = "api:clinic:prescription:query";
    /** 检查/检验申请（医生） */
    public static final String CLINIC_EXAM_REQUEST = "api:clinic:exam:request";
    /** 停诊申请（医生） */
    public static final String CLINIC_STOP_APPLY = "api:clinic:stop:apply";
    /** 停诊科主任初审 */
    public static final String CLINIC_STOP_CHIEF_REVIEW = "api:clinic:stop:chief-review";
    /** 停诊终审（管理员） */
    public static final String CLINIC_STOP_APPROVE = "api:clinic:stop:approve";
    /** 停诊列表查询 */
    public static final String CLINIC_STOP_QUERY = "api:clinic:stop:query";
    /** BI 统计查询 */
    public static final String CLINIC_BI_QUERY = "api:clinic:bi:query";
    /** 医生分页/详情查询 */
    public static final String CLINIC_DOCTOR_QUERY = "api:clinic:doctor:query";
    /** 新增医生（管理员） */
    public static final String CLINIC_DOCTOR_CREATE = "api:clinic:doctor:create";
    /** 编辑医生（管理员） */
    public static final String CLINIC_DOCTOR_UPDATE = "api:clinic:doctor:update";
    /** 医生状态变更（管理员） */
    public static final String CLINIC_DOCTOR_STATUS = "api:clinic:doctor:status";

    // ==================== 医辅物资（medsupply） ====================

    /** 药品目录分页查询（管理员） */
    public static final String MEDSUPPLY_DRUG_QUERY = "api:medsupply:drug:query";
    /** 新增药品（管理员） */
    public static final String MEDSUPPLY_DRUG_CREATE = "api:medsupply:drug:create";
    /** 编辑药品（管理员） */
    public static final String MEDSUPPLY_DRUG_UPDATE = "api:medsupply:drug:update";
    /** 库存列表查询（管理员） */
    public static final String MEDSUPPLY_INVENTORY_QUERY = "api:medsupply:inventory:query";
    /** 药品入库（管理员） */
    public static final String MEDSUPPLY_INVENTORY_INBOUND = "api:medsupply:inventory:inbound";
    /** 药品出库（管理员） */
    public static final String MEDSUPPLY_INVENTORY_OUTBOUND = "api:medsupply:inventory:outbound";
    /** 库存盘点调整（管理员） */
    public static final String MEDSUPPLY_INVENTORY_ADJUST = "api:medsupply:inventory:adjust";
    /** 处方审核（药师） */
    public static final String MEDSUPPLY_DISPENSE_REVIEW = "api:medsupply:dispense:review";
    /** 发药确认（药师） */
    public static final String MEDSUPPLY_DISPENSE_EXEC = "api:medsupply:dispense:exec";
    /** 发药记录查询（药师） */
    public static final String MEDSUPPLY_DISPENSE_QUERY = "api:medsupply:dispense:query";
    /** 检查项目列表（管理员） */
    public static final String MEDSUPPLY_EXAM_ITEM_QUERY = "api:medsupply:exam-item:query";
    /** 创建检查项目（管理员） */
    public static final String MEDSUPPLY_EXAM_ITEM_CREATE = "api:medsupply:exam-item:create";
    /** 检查报告查询（患者本人） */
    public static final String MEDSUPPLY_EXAM_REPORT_QUERY = "api:medsupply:exam-report:query";
    /** 影像检查执行（影像技师） */
    public static final String MEDSUPPLY_EXAM_EXEC = "api:medsupply:exam:exec";
    /** 检验执行（检验技师） */
    public static final String MEDSUPPLY_LAB_EXEC = "api:medsupply:lab:exec";
    /** 检查/检验报告录入（技师） */
    public static final String MEDSUPPLY_EXAM_REPORT_CREATE = "api:medsupply:exam-report:create";
    /** 报告审核（技师/管理员） */
    public static final String MEDSUPPLY_EXAM_REPORT_AUDIT = "api:medsupply:exam-report:audit";
    /** 未缴费检查申请查询（患者本人/收费员） */
    public static final String MEDSUPPLY_EXAM_UNPAID = "api:medsupply:exam:unpaid";
    /** 药品查询（医生开处方用） */
    public static final String MEDSUPPLY_DRUG_SEARCH = "api:medsupply:drug:search";
    /** 输液医嘱开具（医生） */
    public static final String MEDSUPPLY_INFUSION_CREATE = "api:medsupply:infusion:create";
    /** 输液单查询（患者本人） */
    public static final String MEDSUPPLY_INFUSION_QUERY = "api:medsupply:infusion:query";
    /** 未缴费输液单查询（患者本人/收费员） */
    public static final String MEDSUPPLY_INFUSION_UNPAID = "api:medsupply:infusion:unpaid";
    /** 护士站输液执行（护士） */
    public static final String MEDSUPPLY_INFUSION_EXEC = "api:medsupply:infusion:exec";

    // ==================== 支付结算（payment） ====================

    /** 挂号支付（患者本人） */
    public static final String PAYMENT_PAY = "api:payment:pay";
    /** 订单状态查询 */
    public static final String PAYMENT_STATUS_QUERY = "api:payment:status:query";
    /** 挂号凭证下载 */
    public static final String PAYMENT_RECEIPT = "api:payment:receipt:query";
    /** 扫表关单（管理员） */
    public static final String PAYMENT_SCAN_TIMEOUT = "api:payment:scan-timeout";
    /** 创建诊疗费订单（患者） */
    public static final String PAYMENT_TREATMENT_CREATE = "api:payment:treatment:create";
    /** 支付诊疗费（患者） */
    public static final String PAYMENT_TREATMENT_PAY = "api:payment:treatment:pay";
    /** 收费员代缴费建单 */
    public static final String PAYMENT_CASHIER_ORDER = "api:payment:cashier:order";
    /** 收费员代缴费 */
    public static final String PAYMENT_CASHIER_PAY = "api:payment:cashier:pay";
    /** 收费员退费 */
    public static final String PAYMENT_CASHIER_REFUND = "api:payment:cashier:refund";
    /** 日结汇总查询（收费员） */
    public static final String PAYMENT_SETTLE_QUERY = "api:payment:settle:query";
    /** 生成日结单（收费员） */
    public static final String PAYMENT_SETTLE_CREATE = "api:payment:settle:create";
    /** 站内信列表（患者本人） */
    public static final String PAYMENT_NOTIFY_QUERY = "api:payment:notify:query";
    /** 站内信标记已读（患者本人/管理员） */
    public static final String PAYMENT_NOTIFY_READ = "api:payment:notify:read";

    // ==================== AI 智能分诊（ai） ====================

    /** AI 智能分诊（患者） */
    public static final String AI_TRIAGE = "api:ai:triage";

    // ==================== 菜单 / 按钮权限（前端用） ====================

    /** 管理后台 Dashboard */
    public static final String MENU_ADMIN_DASHBOARD = "menu:admin:dashboard";
    /** 管理后台-科室管理 */
    public static final String MENU_ADMIN_DEPARTMENT = "menu:admin:department";
    /** 管理后台-医生管理 */
    public static final String MENU_ADMIN_DOCTOR = "menu:admin:doctor";
    /** 管理后台-排班管理 */
    public static final String MENU_ADMIN_SCHEDULE = "menu:admin:schedule";
    /** 管理后台-号源查询 */
    public static final String MENU_ADMIN_SLOT = "menu:admin:slot";
    /** 管理后台-预约管理 */
    public static final String MENU_ADMIN_APPOINTMENT = "menu:admin:appointment";
    /** 管理后台-药品管理 */
    public static final String MENU_ADMIN_DRUG = "menu:admin:drug";
    /** 管理后台-检查检验 */
    public static final String MENU_ADMIN_EXAM = "menu:admin:exam";
    /** 管理后台-停诊审批 */
    public static final String MENU_ADMIN_STOP = "menu:admin:stop";
    /** 管理后台-用户管理 */
    public static final String MENU_ADMIN_USER = "menu:admin:user";
    /** 管理后台-审计日志 */
    public static final String MENU_ADMIN_AUDIT = "menu:admin:audit";
    /** 管理后台-BI 统计 */
    public static final String MENU_ADMIN_BI = "menu:admin:bi";
    /** 管理员-创建用户按钮 */
    public static final String BTN_ADMIN_USER_CREATE = "btn:admin:user:create";
    /** 管理员-分配角色按钮 */
    public static final String BTN_ADMIN_ASSIGN_ROLE = "btn:admin:assign-role";
    /** 管理员-排班确认/驳回按钮 */
    public static final String BTN_ADMIN_SCHEDULE_CONFIRM = "btn:admin:schedule:confirm";
    /** 管理员-停诊终审按钮 */
    public static final String BTN_ADMIN_STOP_APPROVE = "btn:admin:stop:approve";
    /** 医生工作台 */
    public static final String MENU_DOCTOR_WORKBENCH = "menu:doctor:workbench";
    /** 医生-接诊/病历/处方按钮 */
    public static final String BTN_DOCTOR_CONSULT = "btn:doctor:consult";
    /** 医生-叫号按钮 */
    public static final String BTN_DOCTOR_CALL = "btn:doctor:call";
    /** 药师-审核/发药按钮 */
    public static final String BTN_PHARMACIST_DISPENSE = "btn:pharmacist:dispense";
    /** 收费台 */
    public static final String MENU_CASHIER = "menu:cashier:workbench";
    /** 收费员-代缴费/退费/日结按钮 */
    public static final String BTN_CASHIER_OPERATE = "btn:cashier:operate";
    /** 分诊台 */
    public static final String MENU_TRIAGE = "menu:triage:workbench";
    /** 检验技师工作台 */
    public static final String MENU_LAB_TECH = "menu:lab-tech:workbench";
    /** 影像技师工作台 */
    public static final String MENU_EXAM_TECH = "menu:exam-tech:workbench";
    /** 护士站 */
    public static final String MENU_NURSE = "menu:nurse:workbench";
    /** 患者中心 */
    public static final String MENU_PATIENT_CENTER = "menu:patient:center";

    /**
     * 超管通配权限码：拥有该码视为拥有全部权限。
     * <p>
     * 超管（{@link RoleConstant#SUPER_ADMIN}）无需逐条绑定，拦截器遇到本码直接放行。
     */
    public static final String ALL_PERMISSIONS = "*:*:*";
}