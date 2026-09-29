-- ============================================================
-- V7：权限底座 — permission 种子数据 + role_permission 绑定（迭代 5 阶段 1）
-- 说明：
--   1. 激活 permission 表：灌入与 PermissionConstant 一一对应的权限码
--      （api:* 接口 / menu:* 菜单 / btn:* 按钮，约 90 项）
--   2. role_permission 绑定：与当前代码权限判断「现状等价」——
--      管理员拥有除角色写操作外的全量权限（含医生/收费等业务，对齐 isXxxOrAdmin 放行）
--      后续阶段再按工作台收紧
--   3. 超管（ROLE_SUPER_ADMIN）绑通配码 *:*:*，拦截器遇到超管角色或通配码直接放行
--   4. 幂等：全部使用 INSERT ... SELECT + NOT EXISTS，可安全重复执行
-- ============================================================

-- ==================== 1. 权限种子（API 接口权限） ====================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    -- ---- auth：角色 / 用户 / 审计 ----
    SELECT 'api:auth:role:query'      AS perm_code, '查询角色'      AS perm_name, 1010 AS sort_order  FROM dual UNION ALL
    SELECT 'api:auth:role:create'     , '创建角色', 1011  FROM dual UNION ALL
    SELECT 'api:auth:role:update'     , '编辑角色', 1012  FROM dual UNION ALL
    SELECT 'api:auth:role:delete'     , '删除角色', 1013  FROM dual UNION ALL
    SELECT 'api:auth:role:assign'     , '分配角色', 1014  FROM dual UNION ALL
    SELECT 'api:auth:user:query'      , '查询用户', 1020  FROM dual UNION ALL
    SELECT 'api:auth:user:create'     , '创建用户', 1021  FROM dual UNION ALL
    SELECT 'api:auth:user:update'     , '变更用户状态', 1022  FROM dual UNION ALL
    SELECT 'api:auth:audit:query'     , '查询审计日志', 1030  FROM dual UNION ALL
    -- ---- patient：实名 / 档案（查询本人接口不挂码，仅管理员审核挂码） ----
    SELECT 'api:patient:realname:review', '实名认证审核', 1100  FROM dual UNION ALL
    -- ---- clinic：科室 / 排班 / 号源 / 预约 / 签到叫号 / 接诊 / 处方 / 停诊 / BI / 医生 ----
    SELECT 'api:clinic:dept:query'    , '查询科室', 2000  FROM dual UNION ALL
    SELECT 'api:clinic:dept:create'   , '新增科室', 2001  FROM dual UNION ALL
    SELECT 'api:clinic:dept:update'   , '编辑科室', 2002  FROM dual UNION ALL
    SELECT 'api:clinic:dept:status'   , '科室状态变更', 2003  FROM dual UNION ALL
    SELECT 'api:clinic:doctor:query'  , '查询医生', 2010  FROM dual UNION ALL
    SELECT 'api:clinic:doctor:create' , '新增医生', 2011  FROM dual UNION ALL
    SELECT 'api:clinic:doctor:update' , '编辑医生', 2012  FROM dual UNION ALL
    SELECT 'api:clinic:doctor:status' , '医生状态变更', 2013  FROM dual UNION ALL
    SELECT 'api:clinic:schedule:create'  , '提交排班', 2020  FROM dual UNION ALL
    SELECT 'api:clinic:schedule:confirm' , '确认排班', 2021  FROM dual UNION ALL
    SELECT 'api:clinic:schedule:reject'  , '驳回排班', 2022  FROM dual UNION ALL
    SELECT 'api:clinic:schedule:cancel'  , '取消排班', 2023  FROM dual UNION ALL
    SELECT 'api:clinic:slot:query'    , '查询号源', 2030  FROM dual UNION ALL
    SELECT 'api:clinic:appointment:create', '挂号下单', 2040  FROM dual UNION ALL
    SELECT 'api:clinic:appointment:query' , '查询预约', 2041  FROM dual UNION ALL
    SELECT 'api:clinic:appointment:page'  , '预约分页(管理端)', 2042  FROM dual UNION ALL
    SELECT 'api:clinic:appointment:cancel', '取消预约', 2043  FROM dual UNION ALL
    SELECT 'api:clinic:checkin:create'  , '患者签到', 2050  FROM dual UNION ALL
    SELECT 'api:clinic:checkin:query'   , '排队状态查询', 2051  FROM dual UNION ALL
    SELECT 'api:clinic:checkin:snapshot', '排队快照', 2052  FROM dual UNION ALL
    SELECT 'api:clinic:call:next'    , '叫号', 2060  FROM dual UNION ALL
    SELECT 'api:clinic:call:recall'  , '重呼', 2061  FROM dual UNION ALL
    SELECT 'api:clinic:call:missed'  , '过号', 2062  FROM dual UNION ALL
    SELECT 'api:clinic:consult:start'  , '开始接诊', 2070  FROM dual UNION ALL
    SELECT 'api:clinic:consult:save'   , '保存病历', 2071  FROM dual UNION ALL
    SELECT 'api:clinic:consult:query'  , '病历详情/列表', 2072  FROM dual UNION ALL
    SELECT 'api:clinic:consult:patient', '患者病历列表', 2073  FROM dual UNION ALL
    SELECT 'api:clinic:consult:finish' , '结束就诊', 2074  FROM dual UNION ALL
    SELECT 'api:clinic:consult:today'  , '今日接诊队列', 2075  FROM dual UNION ALL
    SELECT 'api:clinic:prescription:create', '开具处方', 2080  FROM dual UNION ALL
    SELECT 'api:clinic:prescription:query' , '处方查询(未缴费)', 2081  FROM dual UNION ALL
    SELECT 'api:clinic:exam:request'  , '检查/检验申请', 2082  FROM dual UNION ALL
    SELECT 'api:clinic:stop:apply'      , '停诊申请', 2090  FROM dual UNION ALL
    SELECT 'api:clinic:stop:chief-review', '停诊科主任初审', 2091  FROM dual UNION ALL
    SELECT 'api:clinic:stop:approve'   , '停诊终审', 2092  FROM dual UNION ALL
    SELECT 'api:clinic:stop:query'     , '停诊列表查询', 2093  FROM dual UNION ALL
    SELECT 'api:clinic:bi:query'       , 'BI统计', 2100  FROM dual UNION ALL
    -- ---- medsupply：药品 / 库存 / 发药 / 检查项目 / 报告 / 执行 / 输液 ----
    SELECT 'api:medsupply:drug:query'    , '药品目录查询', 3000  FROM dual UNION ALL
    SELECT 'api:medsupply:drug:create'   , '新增药品', 3001  FROM dual UNION ALL
    SELECT 'api:medsupply:drug:update'   , '编辑药品', 3002  FROM dual UNION ALL
    SELECT 'api:medsupply:drug:search'   , '药品搜索(医生开方)', 3003  FROM dual UNION ALL
    SELECT 'api:medsupply:inventory:query'  , '库存查询', 3010  FROM dual UNION ALL
    SELECT 'api:medsupply:inventory:inbound' , '药品入库', 3011  FROM dual UNION ALL
    SELECT 'api:medsupply:inventory:outbound', '药品出库', 3012  FROM dual UNION ALL
    SELECT 'api:medsupply:inventory:adjust'  , '库存盘点', 3013  FROM dual UNION ALL
    SELECT 'api:medsupply:dispense:review', '处方审核', 3020  FROM dual UNION ALL
    SELECT 'api:medsupply:dispense:exec'  , '发药确认', 3021  FROM dual UNION ALL
    SELECT 'api:medsupply:dispense:query' , '发药记录查询', 3022  FROM dual UNION ALL
    SELECT 'api:medsupply:exam-item:query' , '检查项目查询', 3030  FROM dual UNION ALL
    SELECT 'api:medsupply:exam-item:create', '创建检查项目', 3031  FROM dual UNION ALL
    SELECT 'api:medsupply:exam-report:query' , '检查报告查询', 3040  FROM dual UNION ALL
    SELECT 'api:medsupply:exam-report:create', '检查报告录入', 3041  FROM dual UNION ALL
    SELECT 'api:medsupply:exam-report:audit' , '报告审核', 3042  FROM dual UNION ALL
    SELECT 'api:medsupply:exam:exec'   , '影像检查执行', 3050  FROM dual UNION ALL
    SELECT 'api:medsupply:lab:exec'    , '检验执行', 3051  FROM dual UNION ALL
    SELECT 'api:medsupply:exam:unpaid' , '未缴费检查申请查询', 3052  FROM dual UNION ALL
    SELECT 'api:medsupply:infusion:create', '开输液医嘱', 3060  FROM dual UNION ALL
    SELECT 'api:medsupply:infusion:query' , '输液单查询', 3061  FROM dual UNION ALL
    SELECT 'api:medsupply:infusion:unpaid', '未缴费输液单查询', 3062  FROM dual UNION ALL
    SELECT 'api:medsupply:infusion:exec'  , '输液执行', 3063  FROM dual UNION ALL
    -- ---- payment：支付 / 退费 / 日结 / 站内信 ----
    SELECT 'api:payment:pay'           , '挂号支付', 4000  FROM dual UNION ALL
    SELECT 'api:payment:status:query'  , '订单状态查询', 4001  FROM dual UNION ALL
    SELECT 'api:payment:receipt:query' , '挂号凭证下载', 4002  FROM dual UNION ALL
    SELECT 'api:payment:scan-timeout'  , '扫表关单', 4003  FROM dual UNION ALL
    SELECT 'api:payment:treatment:create', '创建诊疗费订单', 4010  FROM dual UNION ALL
    SELECT 'api:payment:treatment:pay'   , '支付诊疗费', 4011  FROM dual UNION ALL
    SELECT 'api:payment:cashier:order'   , '收费员代缴费建单', 4020  FROM dual UNION ALL
    SELECT 'api:payment:cashier:pay'     , '收费员代缴费', 4021  FROM dual UNION ALL
    SELECT 'api:payment:cashier:refund'  , '收费员退费', 4022  FROM dual UNION ALL
    SELECT 'api:payment:settle:query'    , '日结汇总查询', 4030  FROM dual UNION ALL
    SELECT 'api:payment:settle:create'   , '生成日结单', 4031  FROM dual UNION ALL
    SELECT 'api:payment:notify:query'    , '站内信列表', 4040  FROM dual UNION ALL
    SELECT 'api:payment:notify:read'     , '站内信标记已读', 4041  FROM dual UNION ALL
    -- ---- ai ----
    SELECT 'api:ai:triage'            , 'AI智能分诊', 5000 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- ==================== 2. 菜单 / 按钮权限（前端用） ====================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, tmp.perm_type, 0, tmp.path, tmp.sort_order, 1
FROM (
    -- 管理后台菜单
    SELECT 'menu:admin:dashboard'   AS perm_code, '首页概览' AS perm_name, 'MENU' AS perm_type, '/dashboard' AS path, 10 AS sort_order  FROM dual UNION ALL
    SELECT 'menu:admin:department' , '科室管理', 'MENU', '/departments', 20  FROM dual UNION ALL
    SELECT 'menu:admin:doctor'     , '医生管理', 'MENU', '/doctors', 21  FROM dual UNION ALL
    SELECT 'menu:admin:schedule'   , '排班管理', 'MENU', '/schedules', 22  FROM dual UNION ALL
    SELECT 'menu:admin:slot'       , '号源查询', 'MENU', '/slots', 23  FROM dual UNION ALL
    SELECT 'menu:admin:appointment', '预约管理', 'MENU', '/appointments', 24  FROM dual UNION ALL
    SELECT 'menu:admin:drug'       , '药品管理', 'MENU', '/drugs', 25  FROM dual UNION ALL
    SELECT 'menu:admin:exam'       , '检查检验', 'MENU', '/exam', 26  FROM dual UNION ALL
    SELECT 'menu:admin:stop'       , '停诊审批', 'MENU', '/stop', 27  FROM dual UNION ALL
    SELECT 'menu:admin:user'       , '用户管理', 'MENU', '/users', 28  FROM dual UNION ALL
    SELECT 'menu:admin:audit'      , '审计日志', 'MENU', '/audit-logs', 29  FROM dual UNION ALL
    SELECT 'menu:admin:bi'         , 'BI统计大屏', 'MENU', '/bigscreen', 30  FROM dual UNION ALL
    -- 业务工作台菜单
    SELECT 'menu:doctor:workbench' , '医生工作台', 'MENU', '/workbench', 40  FROM dual UNION ALL
    SELECT 'menu:cashier:workbench', '收费工作台', 'MENU', '/cashier', 41  FROM dual UNION ALL
    SELECT 'menu:triage:workbench' , '分诊台', 'MENU', '/triage', 42  FROM dual UNION ALL
    SELECT 'menu:lab-tech:workbench', '检验技师工作台', 'MENU', '/lab-tech', 43  FROM dual UNION ALL
    SELECT 'menu:exam-tech:workbench', '影像技师工作台', 'MENU', '/exam-tech', 44  FROM dual UNION ALL
    SELECT 'menu:nurse:workbench'  , '护士站', 'MENU', '/infusion-nurse', 45  FROM dual UNION ALL
    SELECT 'menu:patient:center'   , '患者中心', 'MENU', '/patient', 46  FROM dual UNION ALL
    -- 按钮权限
    SELECT 'btn:admin:user:create'    , '创建用户按钮', 'BUTTON', NULL, 50  FROM dual UNION ALL
    SELECT 'btn:admin:assign-role'    , '分配角色按钮', 'BUTTON', NULL, 51  FROM dual UNION ALL
    SELECT 'btn:admin:schedule:confirm', '排班确认按钮', 'BUTTON', NULL, 52  FROM dual UNION ALL
    SELECT 'btn:admin:stop:approve'   , '停诊终审按钮', 'BUTTON', NULL, 53  FROM dual UNION ALL
    SELECT 'btn:doctor:consult'       , '接诊/病历/处方按钮', 'BUTTON', NULL, 60  FROM dual UNION ALL
    SELECT 'btn:doctor:call'          , '叫号按钮', 'BUTTON', NULL, 61  FROM dual UNION ALL
    SELECT 'btn:pharmacist:dispense'  , '审核/发药按钮', 'BUTTON', NULL, 62  FROM dual UNION ALL
    SELECT 'btn:cashier:operate'      , '代缴费/退费/日结按钮', 'BUTTON', NULL, 63 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- ==================== 3. 超管通配权限 ====================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT '*:*:*', '全部权限(超管通配)', 'API', 0, NULL, 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = '*:*:*');

-- ==================== 4. 角色权限绑定 ====================

-- 4.1 ROLE_SUPER_ADMIN → 通配全部权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code = '*:*:*'
WHERE r.role_code = 'ROLE_SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.2 ROLE_ADMIN → 与现状等价的全量管理权限（除角色写操作）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    -- auth
    'api:auth:role:query', 'api:auth:user:query', 'api:auth:user:create', 'api:auth:user:update', 'api:auth:audit:query',
    'api:patient:realname:review',
    -- clinic 管理 + 业务（对齐 isXxxOrAdmin 放行）
    'api:clinic:dept:query', 'api:clinic:dept:create', 'api:clinic:dept:update', 'api:clinic:dept:status',
    'api:clinic:doctor:query', 'api:clinic:doctor:create', 'api:clinic:doctor:update', 'api:clinic:doctor:status',
    'api:clinic:schedule:create', 'api:clinic:schedule:confirm', 'api:clinic:schedule:reject', 'api:clinic:schedule:cancel',
    'api:clinic:slot:query', 'api:clinic:appointment:query', 'api:clinic:appointment:page', 'api:clinic:appointment:cancel',
    'api:clinic:checkin:snapshot', 'api:clinic:call:next', 'api:clinic:call:recall', 'api:clinic:call:missed',
    'api:clinic:consult:start', 'api:clinic:consult:save', 'api:clinic:consult:finish', 'api:clinic:consult:today',
    'api:clinic:prescription:create', 'api:clinic:prescription:query', 'api:clinic:exam:request',
    'api:clinic:stop:apply', 'api:clinic:stop:chief-review', 'api:clinic:stop:approve', 'api:clinic:stop:query',
    'api:clinic:bi:query',
    -- medsupply
    'api:medsupply:drug:query', 'api:medsupply:drug:create', 'api:medsupply:drug:update', 'api:medsupply:drug:search',
    'api:medsupply:inventory:query', 'api:medsupply:inventory:inbound', 'api:medsupply:inventory:outbound', 'api:medsupply:inventory:adjust',
    'api:medsupply:dispense:review', 'api:medsupply:dispense:exec', 'api:medsupply:dispense:query',
    'api:medsupply:exam-item:query', 'api:medsupply:exam-item:create',
    'api:medsupply:exam-report:query', 'api:medsupply:exam-report:create', 'api:medsupply:exam-report:audit',
    'api:medsupply:exam:exec', 'api:medsupply:lab:exec', 'api:medsupply:exam:unpaid',
    'api:medsupply:infusion:create', 'api:medsupply:infusion:query', 'api:medsupply:infusion:unpaid', 'api:medsupply:infusion:exec',
    -- payment
    'api:payment:pay', 'api:payment:status:query', 'api:payment:receipt:query', 'api:payment:scan-timeout',
    'api:payment:cashier:order', 'api:payment:cashier:pay', 'api:payment:cashier:refund',
    'api:payment:settle:query', 'api:payment:settle:create',
    'api:payment:notify:query', 'api:payment:notify:read',
    -- ai
    'api:ai:triage',
    -- 菜单 / 按钮
    'menu:admin:dashboard', 'menu:admin:department', 'menu:admin:doctor', 'menu:admin:schedule',
    'menu:admin:slot', 'menu:admin:appointment', 'menu:admin:drug', 'menu:admin:exam',
    'menu:admin:stop', 'menu:admin:user', 'menu:admin:audit', 'menu:admin:bi',
    'menu:doctor:workbench', 'menu:cashier:workbench', 'menu:triage:workbench',
    'menu:lab-tech:workbench', 'menu:exam-tech:workbench', 'menu:nurse:workbench', 'menu:patient:center',
    'btn:admin:user:create', 'btn:admin:assign-role', 'btn:admin:schedule:confirm', 'btn:admin:stop:approve',
    'btn:doctor:consult', 'btn:doctor:call', 'btn:pharmacist:dispense', 'btn:cashier:operate'
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.3 ROLE_DEPT_CHIEF → 医生全量 + 停诊科主任初审
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:dept:query', 'api:clinic:doctor:query', 'api:clinic:schedule:create', 'api:clinic:slot:query',
    'api:clinic:appointment:query', 'api:clinic:checkin:snapshot',
    'api:clinic:call:next', 'api:clinic:call:recall', 'api:clinic:call:missed',
    'api:clinic:consult:start', 'api:clinic:consult:save', 'api:clinic:consult:finish', 'api:clinic:consult:today',
    'api:clinic:prescription:create', 'api:clinic:exam:request',
    'api:clinic:stop:apply', 'api:clinic:stop:chief-review', 'api:clinic:stop:query',
    'api:medsupply:drug:search', 'api:medsupply:infusion:create', 'api:medsupply:infusion:query',
    'api:payment:status:query', 'api:payment:receipt:query',
    'menu:doctor:workbench', 'btn:doctor:consult', 'btn:doctor:call'
)
WHERE r.role_code = 'ROLE_DEPT_CHIEF'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.4 ROLE_DOCTOR → 医生业务权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:dept:query', 'api:clinic:doctor:query', 'api:clinic:schedule:create', 'api:clinic:slot:query',
    'api:clinic:appointment:query', 'api:clinic:checkin:snapshot',
    'api:clinic:call:next', 'api:clinic:call:recall', 'api:clinic:call:missed',
    'api:clinic:consult:start', 'api:clinic:consult:save', 'api:clinic:consult:finish', 'api:clinic:consult:today',
    'api:clinic:prescription:create', 'api:clinic:exam:request',
    'api:clinic:stop:apply', 'api:clinic:stop:query',
    'api:medsupply:drug:search', 'api:medsupply:infusion:create', 'api:medsupply:infusion:query',
    'api:payment:status:query', 'api:payment:receipt:query',
    'menu:doctor:workbench', 'btn:doctor:consult', 'btn:doctor:call'
)
WHERE r.role_code = 'ROLE_DOCTOR'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.5 ROLE_PHARMACIST → 药房（四查十对审核 / 发药）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:dispense:review', 'api:medsupply:dispense:exec', 'api:medsupply:dispense:query',
    'api:medsupply:drug:query',
    'btn:pharmacist:dispense'
)
WHERE r.role_code = 'ROLE_PHARMACIST'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.6 ROLE_EXAM_TECH → 影像检查执行 + 报告录入/审核
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:exam:exec', 'api:medsupply:exam-report:create', 'api:medsupply:exam-report:audit',
    'api:medsupply:exam-report:query',
    'menu:exam-tech:workbench'
)
WHERE r.role_code = 'ROLE_EXAM_TECH'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.7 ROLE_LAB_TECH → 检验执行 + 报告录入/审核
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:lab:exec', 'api:medsupply:exam-report:create', 'api:medsupply:exam-report:audit',
    'api:medsupply:exam-report:query',
    'menu:lab-tech:workbench'
)
WHERE r.role_code = 'ROLE_LAB_TECH'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.8 ROLE_NURSE → 护士站输液执行
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:infusion:exec', 'api:medsupply:infusion:query',
    'menu:nurse:workbench'
)
WHERE r.role_code = 'ROLE_NURSE'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.9 ROLE_TRIAGE_NURSE → 分诊台（签到叫号辅助；现无独立接口校验，先挂菜单）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:checkin:query', 'api:clinic:checkin:snapshot', 'api:clinic:dept:query',
    'menu:triage:workbench'
)
WHERE r.role_code = 'ROLE_TRIAGE_NURSE'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.10 ROLE_CASHIER → 收费台（代缴费 / 退费 / 日结 / 未缴费查询）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:payment:cashier:order', 'api:payment:cashier:pay', 'api:payment:cashier:refund',
    'api:payment:settle:query', 'api:payment:settle:create',
    'api:payment:status:query', 'api:payment:receipt:query',
    'api:clinic:prescription:query', 'api:medsupply:exam:unpaid', 'api:medsupply:infusion:unpaid',
    'menu:cashier:workbench', 'btn:cashier:operate'
)
WHERE r.role_code = 'ROLE_CASHIER'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.11 ROLE_PATIENT → 患者中心（挂号 / 查询 / 支付 / 站内信 / 报告）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:dept:query', 'api:clinic:doctor:query', 'api:clinic:slot:query',
    'api:clinic:appointment:create', 'api:clinic:appointment:query', 'api:clinic:appointment:cancel',
    'api:clinic:checkin:create', 'api:clinic:checkin:query',
    'api:clinic:consult:query', 'api:clinic:consult:patient', 'api:clinic:prescription:query',
    'api:medsupply:exam-report:query', 'api:medsupply:exam:unpaid', 'api:medsupply:infusion:query', 'api:medsupply:infusion:unpaid',
    'api:payment:pay', 'api:payment:status:query', 'api:payment:receipt:query',
    'api:payment:treatment:create', 'api:payment:treatment:pay',
    'api:payment:notify:query', 'api:payment:notify:read',
    'api:ai:triage',
    'menu:patient:center'
)
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
