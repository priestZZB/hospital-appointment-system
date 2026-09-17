-- ============================================================
-- auth_db V10：功能补全第一批权限 — 会诊 / 转诊 / 随访 / 医疗证明
-- 说明：
--   1. 新增 clinic 业务权限码（会诊 / 转诊 / 随访 / 证明）
--   2. 角色绑定：医生/科主任全量业务，管理员可见并处理，患者仅查询
--   幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

-- 1. 权限种子
INSERT INTO `permission` (`perm_code`, `perm_name`, `perm_type`, `parent_id`, `path`, `sort_order`, `status`)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:clinic:consult-request:create'  AS perm_code, '发起会诊'   AS perm_name, 2083 AS sort_order UNION ALL
    SELECT 'api:clinic:consult-request:handle'  , '处理会诊', 2084 UNION ALL
    SELECT 'api:clinic:consult-request:query'   , '查询会诊', 2085 UNION ALL
    SELECT 'api:clinic:referral:create'         , '创建转诊单', 2086 UNION ALL
    SELECT 'api:clinic:referral:handle'         , '处理转诊', 2087 UNION ALL
    SELECT 'api:clinic:referral:query'          , '查询转诊', 2088 UNION ALL
    SELECT 'api:clinic:follow-up:create'        , '创建随访计划', 2089 UNION ALL
    SELECT 'api:clinic:follow-up:record'        , '随访记录', 2090 UNION ALL
    SELECT 'api:clinic:follow-up:query'         , '查询随访', 2091 UNION ALL
    SELECT 'api:clinic:certificate:create'      , '开具证明', 2092 UNION ALL
    SELECT 'api:clinic:certificate:query'       , '查询证明', 2093 UNION ALL
    SELECT 'api:clinic:certificate:download'    , '下载证明', 2094
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.perm_code = tmp.perm_code);

-- 2. 菜单权限
INSERT INTO `permission` (`perm_code`, `perm_name`, `perm_type`, `parent_id`, `path`, `sort_order`, `status`)
SELECT tmp.perm_code, tmp.perm_name, 'MENU', 0, tmp.path, tmp.sort_order, 1
FROM (
    SELECT 'menu:doctor:consult-request' AS perm_code, '会诊管理' AS perm_name, '/consult-request', 44 AS sort_order UNION ALL
    SELECT 'menu:doctor:referral'        , '转诊管理', '/referral', 45 UNION ALL
    SELECT 'menu:doctor:follow-up'       , '随访管理', '/follow-up', 46 UNION ALL
    SELECT 'menu:doctor:certificate'     , '医疗证明', '/certificate', 47 UNION ALL
    SELECT 'menu:admin:clinic-extension' , '门诊协同', '/clinic-extension', 48
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.perm_code = tmp.perm_code);

-- 3. 角色绑定
-- 3.1 ROLE_ADMIN：会诊/转诊/随访/证明全部可查可处理
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:clinic:consult-request:create', 'api:clinic:consult-request:handle', 'api:clinic:consult-request:query',
    'api:clinic:referral:create', 'api:clinic:referral:handle', 'api:clinic:referral:query',
    'api:clinic:follow-up:create', 'api:clinic:follow-up:record', 'api:clinic:follow-up:query',
    'api:clinic:certificate:create', 'api:clinic:certificate:query', 'api:clinic:certificate:download',
    'menu:admin:clinic-extension'
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3.2 ROLE_DEPT_CHIEF / ROLE_DOCTOR：会诊/转诊/随访/证明全部业务
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:clinic:consult-request:create', 'api:clinic:consult-request:handle', 'api:clinic:consult-request:query',
    'api:clinic:referral:create', 'api:clinic:referral:handle', 'api:clinic:referral:query',
    'api:clinic:follow-up:create', 'api:clinic:follow-up:record', 'api:clinic:follow-up:query',
    'api:clinic:certificate:create', 'api:clinic:certificate:query', 'api:clinic:certificate:download',
    'menu:doctor:consult-request', 'menu:doctor:referral', 'menu:doctor:follow-up', 'menu:doctor:certificate'
)
WHERE r.role_code IN ('ROLE_DEPT_CHIEF', 'ROLE_DOCTOR')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3.3 ROLE_PATIENT：查询本身会诊/转诊/随访/证明
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:clinic:consult-request:query', 'api:clinic:referral:query',
    'api:clinic:follow-up:query', 'api:clinic:certificate:query', 'api:clinic:certificate:download'
)
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
