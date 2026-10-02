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

    // ==================== 功能补全（迭代6 第一批） ====================

    /** 发起会诊 */
    public static final String CLINIC_CONSULT_REQUEST_CREATE = "api:clinic:consult-request:create";
    /** 处理会诊（接受/填写结论/完成/拒绝） */
    public static final String CLINIC_CONSULT_REQUEST_HANDLE = "api:clinic:consult-request:handle";
    /** 查询会诊 */
    public static final String CLINIC_CONSULT_REQUEST_QUERY = "api:clinic:consult-request:query";
    /** 创建转诊单 */
    public static final String CLINIC_REFERRAL_CREATE = "api:clinic:referral:create";
    /** 处理转诊（接收/完成/退回） */
    public static final String CLINIC_REFERRAL_HANDLE = "api:clinic:referral:handle";
    /** 查询转诊 */
    public static final String CLINIC_REFERRAL_QUERY = "api:clinic:referral:query";
    /** 创建随访计划 */
    public static final String CLINIC_FOLLOW_UP_CREATE = "api:clinic:follow-up:create";
    /** 随访记录回填 */
    public static final String CLINIC_FOLLOW_UP_RECORD = "api:clinic:follow-up:record";
    /** 查询随访 */
    public static final String CLINIC_FOLLOW_UP_QUERY = "api:clinic:follow-up:query";
    /** 开具证明 */
    public static final String CLINIC_CERTIFICATE_CREATE = "api:clinic:certificate:create";
    /** 查询证明 */
    public static final String CLINIC_CERTIFICATE_QUERY = "api:clinic:certificate:query";
    /** 下载证明 PDF */
    public static final String CLINIC_CERTIFICATE_DOWNLOAD = "api:clinic:certificate:download";

    /** 菜单：会诊管理 */
    public static final String MENU_DOCTOR_CONSULT_REQUEST = "menu:doctor:consult-request";
    /** 菜单：转诊管理 */
    public static final String MENU_DOCTOR_REFERRAL = "menu:doctor:referral";
    /** 菜单：随访管理 */
    public static final String MENU_DOCTOR_FOLLOW_UP = "menu:doctor:follow-up";
    /** 菜单：医疗证明 */
    public static final String MENU_DOCTOR_CERTIFICATE = "menu:doctor:certificate";
    /** 菜单：门诊协同（管理员） */
    public static final String MENU_ADMIN_CLINIC_EXTENSION = "menu:admin:clinic-extension";

    // ==================== 功能补全（迭代6 第二批：危急值 + 处方点评） ====================

    /** 危急值上报（检验/检查技师） */
    public static final String MEDSUPPLY_CRITICAL_REPORT = "api:medsupply:critical:report";
    /** 危急值复核（医生/主任） */
    public static final String MEDSUPPLY_CRITICAL_CONFIRM = "api:medsupply:critical:confirm";
    /** 危急值查询 */
    public static final String MEDSUPPLY_CRITICAL_QUERY = "api:medsupply:critical:query";
    /** 处方点评（药师） */
    public static final String MEDSUPPLY_PRESCRIPTION_REVIEW_CREATE = "api:medsupply:prescription-review:create";
    /** 处方点评查询 */
    public static final String MEDSUPPLY_PRESCRIPTION_REVIEW_QUERY = "api:medsupply:prescription-review:query";
    // ==================== 住院服务（inpatient · 迭代6 A1） ====================

    /** 入院登记 */
    public static final String INPATIENT_ADMISSION_CREATE = "api:inpatient:admission:create";
    /** 住院查询（患者/医生/管理员） */
    public static final String INPATIENT_ADMISSION_QUERY = "api:inpatient:admission:query";
    /** 床位查询 */
    public static final String INPATIENT_BED_QUERY = "api:inpatient:bed:query";
    /** 分床/转床（护士） */
    public static final String INPATIENT_BED_ASSIGN = "api:inpatient:bed:assign";
    /** 开立医嘱（医生） */
    public static final String INPATIENT_ORDER_CREATE = "api:inpatient:order:create";
    /** 医嘱核对（护士） */
    public static final String INPATIENT_ORDER_CONFIRM = "api:inpatient:order:confirm";
    /** 医嘱执行（护士） */
    public static final String INPATIENT_ORDER_EXECUTE = "api:inpatient:order:execute";
    /** 停止医嘱（医生） */
    public static final String INPATIENT_ORDER_STOP = "api:inpatient:order:stop";
    /** 医嘱查询 */
    public static final String INPATIENT_ORDER_QUERY = "api:inpatient:order:query";
    /** 生命体征录入（护士） */
    public static final String INPATIENT_VITAL_RECORD = "api:inpatient:vital:record";
    /** 预交金缴纳（收费员） */
    public static final String INPATIENT_DEPOSIT_PAY = "api:inpatient:deposit:pay";
    /** 出院小结与结算（医生/收费员） */
    public static final String INPATIENT_DISCHARGE = "api:inpatient:discharge";
    // ==================== 住院扩展（inpatient · 迭代6 E1~E6） ====================

    /** 转科（医生） */
    public static final String INPATIENT_TRANSFER_DEPT = "api:inpatient:transfer";
    /** 住院总览看板（医护） */
    public static final String INPATIENT_OVERVIEW = "api:inpatient:overview";
    /** 护理病历录入（护士） */
    public static final String INPATIENT_NURSING_RECORD = "api:inpatient:nursing:record";
    /** 护理病历查询 */
    public static final String INPATIENT_NURSING_QUERY = "api:inpatient:nursing:query";
    /** 住院会诊发起（医生） */
    public static final String INPATIENT_CONSULT_CREATE = "api:inpatient:consult:create";
    /** 住院会诊处理（受邀科室医生） */
    public static final String INPATIENT_CONSULT_HANDLE = "api:inpatient:consult:handle";
    /** 住院会诊查询 */
    public static final String INPATIENT_CONSULT_QUERY = "api:inpatient:consult:query";
    /** 手术申请（医生） */
    public static final String INPATIENT_SURGERY_APPLY = "api:inpatient:surgery:apply";
    /** 手术申请排台/取消 */
    public static final String INPATIENT_SURGERY_SCHEDULE = "api:inpatient:surgery:schedule";
    /** 手术申请查询 */
    public static final String INPATIENT_SURGERY_QUERY = "api:inpatient:surgery:query";
    /** 住院费用登记（护士/收费员） */
    public static final String INPATIENT_FEE_POST = "api:inpatient:fee:post";
    /** 住院费用查询（含患者本人） */
    public static final String INPATIENT_FEE_QUERY = "api:inpatient:fee:query";
    /** 床位费日结（管理员） */
    public static final String INPATIENT_FEE_DAILY = "api:inpatient:fee:daily";
    /** 病案首页查询 */
    public static final String INPATIENT_HOME_QUERY = "api:inpatient:home:query";

    /** 菜单：住院医生站 */
    public static final String MENU_INPATIENT_DOCTOR = "menu:inpatient:doctor";
    /** 菜单：住院护士站 */
    public static final String MENU_INPATIENT_NURSE = "menu:inpatient:nurse";

    // ==================== 药事管理（medsupply · 迭代7 B1~B10，对应 auth V14 植入） ====================

    /** 药品三分类管理（WESTERN/CHINESE_PATENT/HERBAL） */
    public static final String MEDSUPPLY_DRUG_TYPE_MANAGE = "api:medsupply:drug:type:manage";
    /** 药库批次/效期查询 */
    public static final String MEDSUPPLY_DRUG_BATCH_LIST = "api:medsupply:drug:batch:list";
    /** 采购入库批次 */
    public static final String MEDSUPPLY_DRUG_BATCH_CREATE = "api:medsupply:drug:batch:create";
    /** 药品养护报损 */
    public static final String MEDSUPPLY_DRUG_SCRAP = "api:medsupply:drug:scrap";
    /** 退药冲账 */
    public static final String MEDSUPPLY_DRUG_RETURN_CREATE = "api:medsupply:drug:return:create";
    /** 药品调拨 */
    public static final String MEDSUPPLY_DRUG_TRANSFER_CREATE = "api:medsupply:drug:transfer:create";
    /** 麻精五专登记 */
    public static final String MEDSUPPLY_NARCOTIC_REGISTER = "api:medsupply:narcotic:register";
    /** 麻精登记查询 */
    public static final String MEDSUPPLY_NARCOTIC_QUERY = "api:medsupply:narcotic:query";
    /** 中药代煎下单（患者/医生） */
    public static final String MEDSUPPLY_DECOCTION_CREATE = "api:medsupply:decoction:create";
    /** 代煎状态流转（药师） */
    public static final String MEDSUPPLY_DECOCTION_MANAGE = "api:medsupply:decoction:manage";
    /** 代煎查询（患者本人/药师） */
    public static final String MEDSUPPLY_DECOCTION_QUERY = "api:medsupply:decoction:query";
    /** CDSS 合理用药规则管理 */
    public static final String MEDSUPPLY_DRUGRULE_MANAGE = "api:medsupply:drugrule:manage";
    /** 抗菌药物分级授权管理 */
    public static final String MEDSUPPLY_ANTIBIOTIC_AUTH = "api:medsupply:antibiotic:auth";
    /** 用药指导单打印 */
    public static final String MEDSUPPLY_GUIDANCE_PRINT = "api:medsupply:guidance:print";

    // ==================== 迭代8 检验 LIS + 影像中心 ====================
    /** 标本采集 */
    public static final String MEDSUPPLY_SPECIMEN_COLLECT = "api:medsupply:specimen:collect";
    /** 标本核收 */
    public static final String MEDSUPPLY_SPECIMEN_RECEIVE = "api:medsupply:specimen:receive";
    /** 标本查询 */
    public static final String MEDSUPPLY_SPECIMEN_QUERY = "api:medsupply:specimen:query";
    /** 检验结果录入 */
    public static final String MEDSUPPLY_RESULT_ENTRY = "api:medsupply:result:entry";
    /** 检验结果查询 */
    public static final String MEDSUPPLY_RESULT_QUERY = "api:medsupply:result:query";
    /** 化验单打印 */
    public static final String MEDSUPPLY_LABREPORT_PRINT = "api:medsupply:labreport:print";
    /** 检查预约 */
    public static final String MEDSUPPLY_EXAMRESV_BOOK = "api:medsupply:examresv:book";
    /** 检查报到 */
    public static final String MEDSUPPLY_EXAMRESV_CHECKIN = "api:medsupply:examresv:checkin";
    /** 检查预约查询 */
    public static final String MEDSUPPLY_EXAMRESV_QUERY = "api:medsupply:examresv:query";
    /** 影像上传 */
    public static final String MEDSUPPLY_IMAGE_UPLOAD = "api:medsupply:image:upload";
    /** 影像查询 */
    public static final String MEDSUPPLY_IMAGE_QUERY = "api:medsupply:image:query";
    /** 报告模板管理 */
    public static final String MEDSUPPLY_TEMPLATE_MANAGE = "api:medsupply:template:manage";
    /** 云影像链接生成 */
    public static final String MEDSUPPLY_CLOUDLINK_CREATE = "api:medsupply:cloudlink:create";
    /** 云影像查看 */
    public static final String MEDSUPPLY_CLOUDLINK_VIEW = "api:medsupply:cloudlink:view";

    // ==================== 迭代9 门诊流程补强（对应 auth V16 植入） ====================

    /** 分诊设置优先级（分诊护士：checkin.priority + Redis 队列 score 调整） */
    public static final String CLINIC_TRIAGE_SET_PRIORITY = "api:clinic:triage:set-priority";
    /** 分诊台队列查看（按分诊优先级排序的 WAITING 队列） */
    public static final String CLINIC_TRIAGE_QUEUE = "api:clinic:triage:queue";
    /** 回诊标记（检查/检验完成后重新排队，同档插队） */
    public static final String CLINIC_REVISIT_MARK = "api:clinic:revisit:mark";
    /** 医生加号（号源约满后超挂） */
    public static final String CLINIC_OVERBOOK_CREATE = "api:clinic:overbook:create";
    /** 加号开关管理（排班维度 schedule.overbook 设置） */
    public static final String CLINIC_OVERBOOK_MANAGE = "api:clinic:overbook:manage";
    /** 退号改期（未就诊预约更换号源） */
    public static final String CLINIC_RESCHEDULE_APPLY = "api:clinic:reschedule:apply";
    /** 绿色通道管理（号源 channel_type 设置与批量划绿） */
    public static final String CLINIC_GREENCHANNEL_MANAGE = "api:clinic:greenchannel:manage";
    /** 分层定价设置（排班 fee_type 普通/专家号） */
    public static final String CLINIC_PRICEDETAIL_MANAGE = "api:clinic:pricedetail:manage";
    /** 排班日历查看（日期×医生出诊矩阵） */
    public static final String CLINIC_SCHEDULE_CALENDAR = "api:clinic:schedule:calendar";
    /** 自动排班 Job 手动触发（指定日期生成排班+号源） */
    public static final String CLINIC_SCHEDULE_GENERATE = "api:clinic:schedule:generate";
    /** ICD-10 诊断字典管理（管理端 CRUD） */
    public static final String CLINIC_ICD_MANAGE = "api:clinic:icd:manage";
    /** ICD-10 诊断字典查询（分诊/诊疗共用） */
    public static final String CLINIC_ICD_QUERY = "api:clinic:icd:query";

    // ==================== 手术/麻醉中心（inpatient · 迭代10 F1~F5，对应 auth V17 植入） ====================

    /** 手术中心看板查看（医护） */
    public static final String INPATIENT_SURGERY_BOARD = "api:inpatient:surgery:board";
    /** 手术中心列表查询（医护） */
    public static final String INPATIENT_SURGERY_LIST = "api:inpatient:surgery:list";
    /** 门诊手术创建（医生） */
    public static final String INPATIENT_SURGERY_OUTPATIENT_CREATE = "api:inpatient:surgery:outpatient";
    /** 手术排台（统一手术单，与 V12 既有 INPATIENT_SURGERY_SCHEDULE 申请单排台并存） */
    public static final String INPATIENT_SURGERY_CENTER_SCHEDULE = "api:inpatient:surgery:center:schedule";
    /** 术前评估提交（医生） */
    public static final String INPATIENT_SURGERY_PREOP_SUBMIT = "api:inpatient:surgery:preop";
    /** 知情同意签署（医生/护士） */
    public static final String INPATIENT_SURGERY_CONSENT_SIGN = "api:inpatient:surgery:consent";
    /** 手术开始（医生） */
    public static final String INPATIENT_SURGERY_START = "api:inpatient:surgery:start";
    /** 手术记录录入（医生） */
    public static final String INPATIENT_SURGERY_RECORD_ENTRY = "api:inpatient:surgery:record";
    /** 麻醉记录录入（麻醉医生） */
    public static final String INPATIENT_SURGERY_ANESTHESIA_ENTRY = "api:inpatient:surgery:anesthesia";
    /** 术后随访触发（医生） */
    public static final String INPATIENT_SURGERY_POSTOP_FOLLOWUP = "api:inpatient:surgery:postop-followup";

    // ==================== 医保与财务（payment · 迭代11 H1~H4，对应 auth V18 植入） ====================

    /** 医保目录映射管理（管理员） */
    public static final String PAYMENT_INSURANCE_CATALOG_MANAGE = "api:payment:insurance:catalog:manage";
    /** 医保目录映射查询（管理员/医生） */
    public static final String PAYMENT_INSURANCE_CATALOG_QUERY = "api:payment:insurance:catalog:query";
    /** 医保费用结算 */
    public static final String PAYMENT_INSURANCE_SETTLE = "api:payment:insurance:settle";
    /** 医保结算单查询（管理员/医生） */
    public static final String PAYMENT_INSURANCE_SETTLE_QUERY = "api:payment:insurance:settle:query";
    /** 医保票据打印（H3 医疗收费票据 PDF） */
    public static final String PAYMENT_INSURANCE_VOUCHER = "api:payment:insurance:voucher";
    /** 医保结算冲正 */
    public static final String PAYMENT_INSURANCE_REVERSE = "api:payment:insurance:reverse";
    /** 收费项目管理（H4 统一收费目录，管理员） */
    public static final String PAYMENT_CHARGE_ITEM_MANAGE = "api:payment:charge-item:manage";
    /** 收费项目查询（管理员/医生） */
    public static final String PAYMENT_CHARGE_ITEM_QUERY = "api:payment:charge-item:query";

    // ==================== 迭代12 病案与统计 ====================
    /** 门诊日报查询 */
    public static final String CLINIC_STATS_DAILY = "api:clinic:stats:daily";
    /** 门诊月报查询 */
    public static final String CLINIC_STATS_MONTHLY = "api:clinic:stats:monthly";
    /** 门诊报表导出 */
    public static final String CLINIC_STATS_EXPORT = "api:clinic:stats:export";
    /** 住院日报查询 */
    public static final String INPATIENT_STATS_DAILY = "api:inpatient:stats:daily";
    /** 住院月报查询 */
    public static final String INPATIENT_STATS_MONTHLY = "api:inpatient:stats:monthly";
    /** 住院报表导出 */
    public static final String INPATIENT_STATS_EXPORT = "api:inpatient:stats:export";
    /** 上报登记管理（医护） */
    public static final String REPORT_FORM_MANAGE = "api:inpatient:report-form:manage";
    /** 上报审核 */
    public static final String REPORT_FORM_REVIEW = "api:inpatient:report-form:review";
    /** 病案管理 */
    public static final String MEDICAL_RECORD_MANAGE = "api:inpatient:medical-record:manage";
    /** 临床路径管理 */
    public static final String PATIENT_PATH_MANAGE = "api:inpatient:patient-path:manage";
    /** 质控指标看板（迭代12补全 J2） */
    public static final String QUALITY_VIEW = "api:medsupply:quality:view";
    /** 医生工作量统计（迭代12补全 J4） */
    public static final String DOCTOR_WORKLOAD_VIEW = "api:clinic:stats:workload";
    /** 公告管理（迭代13 L3） */
    public static final String NOTICE_MANAGE = "api:clinic:notice:manage";
    /** 评价查看（迭代13 K3） */
    public static final String EVALUATION_VIEW = "api:clinic:evaluation:view";
    /** 图文复诊接诊（迭代13 K1） */
    public static final String CONSULT_HANDLE = "api:clinic:consult:handle";
    /** 购药配送管理（迭代13 K2） */
    public static final String DELIVERY_MANAGE = "api:medsupply:delivery:manage";
    /** 急诊分诊管理（迭代14 G1） */
    public static final String TRIAGE_MANAGE = "api:clinic:triage:manage";
    /** 抢救记录管理（迭代14 G2） */
    public static final String RESCUE_MANAGE = "api:clinic:rescue:manage";
    /** 考勤管理（迭代14 L4） */
    public static final String ATTENDANCE_MANAGE = "api:clinic:attendance:manage";
    /** 耗材管理（迭代14 L1） */
    public static final String CONSUMABLE_MANAGE = "api:medsupply:consumable:manage";
    /** 设备管理（迭代14 L2） */
    public static final String EQUIPMENT_MANAGE = "api:medsupply:equipment:manage";
}
