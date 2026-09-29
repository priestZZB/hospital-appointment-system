-- ============================================================
-- auth_db V13：住院扩展权限（迭代6 E1~E6）
-- 幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:inpatient:transfer'          AS perm_code, '住院转科'        AS perm_name, 2512 AS sort_order  FROM dual UNION ALL
    SELECT 'api:inpatient:overview'          , '住院总览看板'   , 2513  FROM dual UNION ALL
    SELECT 'api:inpatient:nursing:record'    , '护理病历录入'   , 2514  FROM dual UNION ALL
    SELECT 'api:inpatient:nursing:query'     , '护理病历查询'   , 2515  FROM dual UNION ALL
    SELECT 'api:inpatient:consult:create'    , '住院会诊发起'   , 2516  FROM dual UNION ALL
    SELECT 'api:inpatient:consult:handle'    , '住院会诊处理'   , 2517  FROM dual UNION ALL
    SELECT 'api:inpatient:consult:query'     , '住院会诊查询'   , 2518  FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:apply'     , '手术申请'       , 2519  FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:schedule'  , '手术排台取消'   , 2520  FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:query'     , '手术申请查询'   , 2521  FROM dual UNION ALL
    SELECT 'api:inpatient:fee:post'          , '住院费用登记'   , 2522  FROM dual UNION ALL
    SELECT 'api:inpatient:fee:query'         , '住院费用查询'   , 2523  FROM dual UNION ALL
    SELECT 'api:inpatient:fee:daily'         , '床位费日结'     , 2524  FROM dual UNION ALL
    SELECT 'api:inpatient:home:query'        , '病案首页查询'   , 2525 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 医生 / 科主任：转科 + 会诊发起 + 手术申请 + 查询类
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:transfer', 'api:inpatient:overview',
    'api:inpatient:nursing:query', 'api:inpatient:consult:create', 'api:inpatient:consult:query',
    'api:inpatient:surgery:apply', 'api:inpatient:surgery:query',
    'api:inpatient:fee:query', 'api:inpatient:home:query'
)
WHERE r.role_code IN ('ROLE_DOCTOR', 'ROLE_DEPT_CHIEF')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 护士：总览 + 护理病历 + 费用登记 + 会诊/手术查询
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:overview', 'api:inpatient:nursing:record', 'api:inpatient:nursing:query',
    'api:inpatient:fee:post', 'api:inpatient:fee:query',
    'api:inpatient:consult:query', 'api:inpatient:surgery:query'
)
WHERE r.role_code = 'ROLE_NURSE'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 收费员：费用登记/查询 + 患者押金代缴
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:fee:post', 'api:inpatient:fee:query', 'api:inpatient:home:query'
)
WHERE r.role_code = 'ROLE_CASHIER'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 患者：本人费用查询 + 预交金自助缴纳（E5）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:fee:query', 'api:inpatient:deposit:pay', 'api:inpatient:home:query'
)
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 管理员：住院扩展全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code LIKE 'api:inpatient:%'
WHERE r.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 受邀科室会诊处理：所有医生/科主任均可处理指派给本科室的会诊
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code = 'api:inpatient:consult:handle'
WHERE r.role_code IN ('ROLE_DOCTOR', 'ROLE_DEPT_CHIEF')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
