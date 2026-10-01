-- ============================================================
-- auth_db V19：病案与统计权限（迭代12 I1/I2/I3/J1）
-- 说明：
--   1. 新增统计/病案/路径权限码 10 个：
--      门诊日报/月报/导出、住院日报/月报/导出、上报登记/审核、
--      病案管理、临床路径管理。
--   2. 角色绑定：管理员/超管全量；统计与导出另绑 ROLE_DOCTOR；
--      report-form:manage 另绑 ROLE_DOCTOR + ROLE_NURSE（医护均可上报）。
--   幂等：INSERT ... SELECT + NOT EXISTS（重复执行不产生重复行）
-- ============================================================

-- 1. 权限种子
INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:clinic:stats:daily'         AS perm_code, '门诊日报查询' AS perm_name, 3100 AS sort_order FROM dual UNION ALL
    SELECT 'api:clinic:stats:monthly'       , '门诊月报查询',             3101 FROM dual UNION ALL
    SELECT 'api:clinic:stats:export'        , '门诊报表导出',             3102 FROM dual UNION ALL
    SELECT 'api:inpatient:stats:daily'      , '住院日报查询',             3103 FROM dual UNION ALL
    SELECT 'api:inpatient:stats:monthly'    , '住院月报查询',             3104 FROM dual UNION ALL
    SELECT 'api:inpatient:stats:export'     , '住院报表导出',             3105 FROM dual UNION ALL
    SELECT 'api:inpatient:report-form:manage', '上报登记管理',            3106 FROM dual UNION ALL
    SELECT 'api:inpatient:report-form:review' , '上报审核',               3107 FROM dual UNION ALL
    SELECT 'api:inpatient:medical-record:manage', '病案管理',             3108 FROM dual UNION ALL
    SELECT 'api:inpatient:patient-path:manage', '临床路径管理',            3109 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 2. 管理员/超管：全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:stats:daily', 'api:clinic:stats:monthly', 'api:clinic:stats:export',
    'api:inpatient:stats:daily', 'api:inpatient:stats:monthly', 'api:inpatient:stats:export',
    'api:inpatient:report-form:manage', 'api:inpatient:report-form:review',
    'api:inpatient:medical-record:manage', 'api:inpatient:patient-path:manage'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3. 医生：统计查询/导出 + 上报 + 病案 + 路径
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:stats:daily', 'api:clinic:stats:monthly', 'api:clinic:stats:export',
    'api:inpatient:stats:daily', 'api:inpatient:stats:monthly', 'api:inpatient:stats:export',
    'api:inpatient:report-form:manage', 'api:inpatient:report-form:review',
    'api:inpatient:medical-record:manage', 'api:inpatient:patient-path:manage'
)
WHERE r.role_code = 'ROLE_DOCTOR'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4. 护士：上报登记
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code = 'api:inpatient:report-form:manage'
WHERE r.role_code = 'ROLE_NURSE'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
