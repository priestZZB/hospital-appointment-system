-- ============================================================
-- auth_db V16：门诊流程补强权限（迭代9 A1~A8 / J3）
-- 说明：
--   1. 新增 clinic 业务权限码：分诊优先级 / 分诊台队列 / 回诊标记 /
--      医生加号 / 加号开关 / 退号改期 / 绿色通道 / 分层定价 /
--      排班日历 / 自动排班手动触发 / ICD 字典管理与查询
--   2. 角色绑定：管理员/超管/医生全量；分诊护士与门诊护士
--      额外绑定分诊、回诊相关权限（ROLE_TRIAGE_NURSE / ROLE_NURSE
--      均已由 V5/V6 迁移创建）
--   幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

-- 1. 权限种子
INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:clinic:triage:set-priority'   AS perm_code, '分诊设置优先级'     AS perm_name, 2800 AS sort_order FROM dual UNION ALL
    SELECT 'api:clinic:triage:queue'          , '分诊台队列查看',    2801 FROM dual UNION ALL
    SELECT 'api:clinic:revisit:mark'          , '回诊标记',          2802 FROM dual UNION ALL
    SELECT 'api:clinic:overbook:create'       , '医生加号',          2803 FROM dual UNION ALL
    SELECT 'api:clinic:overbook:manage'       , '加号开关管理',      2804 FROM dual UNION ALL
    SELECT 'api:clinic:reschedule:apply'      , '退号改期',          2805 FROM dual UNION ALL
    SELECT 'api:clinic:greenchannel:manage'   , '绿色通道管理',      2806 FROM dual UNION ALL
    SELECT 'api:clinic:pricedetail:manage'    , '分层定价设置',      2807 FROM dual UNION ALL
    SELECT 'api:clinic:schedule:calendar'     , '排班日历查看',      2808 FROM dual UNION ALL
    SELECT 'api:clinic:schedule:generate'     , '自动排班手动触发',  2809 FROM dual UNION ALL
    SELECT 'api:clinic:icd:manage'            , 'ICD字典管理',       2810 FROM dual UNION ALL
    SELECT 'api:clinic:icd:query'             , 'ICD字典查询',       2811 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 2. 管理员/超管/医生：全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:triage:set-priority', 'api:clinic:triage:queue', 'api:clinic:revisit:mark',
    'api:clinic:overbook:create', 'api:clinic:overbook:manage',
    'api:clinic:reschedule:apply',
    'api:clinic:greenchannel:manage', 'api:clinic:pricedetail:manage',
    'api:clinic:schedule:calendar', 'api:clinic:schedule:generate',
    'api:clinic:icd:manage', 'api:clinic:icd:query'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_DOCTOR')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3. 分诊护士 / 门诊护士：分诊设置优先级、分诊台队列、回诊标记、ICD 查询
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:triage:set-priority', 'api:clinic:triage:queue', 'api:clinic:revisit:mark',
    'api:clinic:icd:query'
)
WHERE r.role_code IN ('ROLE_TRIAGE_NURSE', 'ROLE_NURSE')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4. 患者：加号/改期为患者自助操作（A2/A3，患者端发起）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:overbook:create', 'api:clinic:reschedule:apply'
)
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);