-- ============================================================
-- auth_db V12：住院模块权限（迭代6 A1）
-- 幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:inpatient:admission:create' AS perm_code, '入院登记' AS perm_name, 2500 AS sort_order  FROM dual UNION ALL
    SELECT 'api:inpatient:admission:query'  , '住院查询', 2501  FROM dual UNION ALL
    SELECT 'api:inpatient:bed:query'       , '床位查询', 2502  FROM dual UNION ALL
    SELECT 'api:inpatient:bed:assign'      , '分床转床', 2503  FROM dual UNION ALL
    SELECT 'api:inpatient:order:create'    , '开立医嘱', 2504  FROM dual UNION ALL
    SELECT 'api:inpatient:order:confirm'   , '医嘱核对', 2505  FROM dual UNION ALL
    SELECT 'api:inpatient:order:execute'   , '医嘱执行', 2506  FROM dual UNION ALL
    SELECT 'api:inpatient:order:stop'      , '停止医嘱', 2507  FROM dual UNION ALL
    SELECT 'api:inpatient:order:query'     , '医嘱查询', 2508  FROM dual UNION ALL
    SELECT 'api:inpatient:vital:record'    , '生命体征录入', 2509  FROM dual UNION ALL
    SELECT 'api:inpatient:deposit:pay'     , '预交金缴纳', 2510  FROM dual UNION ALL
    SELECT 'api:inpatient:discharge'       , '出院小结与结算', 2511 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'MENU', 0, tmp.path, tmp.sort_order, 1
FROM (
    SELECT 'menu:inpatient:doctor' AS perm_code, '住院医生站' AS perm_name, '/inpatient/doctor' AS path, 49 AS sort_order  FROM dual UNION ALL
    SELECT 'menu:inpatient:nurse'  , '住院护士站', '/inpatient/nurse', 50 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 医生 / 科主任：住院全量业务
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:admission:create', 'api:inpatient:admission:query', 'api:inpatient:bed:query',
    'api:inpatient:order:create', 'api:inpatient:order:confirm', 'api:inpatient:order:execute',
    'api:inpatient:order:stop', 'api:inpatient:order:query', 'api:inpatient:vital:record',
    'api:inpatient:deposit:pay', 'api:inpatient:discharge',
    'menu:inpatient:doctor'
)
WHERE r.role_code IN ('ROLE_DOCTOR', 'ROLE_DEPT_CHIEF')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 护士：床位分配 + 医嘱核对/执行 + 生命体征
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:admission:query', 'api:inpatient:bed:query', 'api:inpatient:bed:assign',
    'api:inpatient:order:confirm', 'api:inpatient:order:execute', 'api:inpatient:order:query',
    'api:inpatient:vital:record',
    'menu:inpatient:nurse'
)
WHERE r.role_code = 'ROLE_NURSE'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 收费员：住院查询 + 预交金 + 出院结算
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:admission:query', 'api:inpatient:deposit:pay', 'api:inpatient:discharge'
)
WHERE r.role_code = 'ROLE_CASHIER'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 管理员：住院全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:admission:create', 'api:inpatient:admission:query',
    'api:inpatient:bed:query', 'api:inpatient:bed:assign',
    'api:inpatient:order:create', 'api:inpatient:order:confirm', 'api:inpatient:order:execute',
    'api:inpatient:order:stop', 'api:inpatient:order:query',
    'api:inpatient:vital:record', 'api:inpatient:deposit:pay', 'api:inpatient:discharge',
    'menu:inpatient:doctor', 'menu:inpatient:nurse'
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 患者：仅查询本人住院信息
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:admission:query', 'api:inpatient:order:query'
)
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
