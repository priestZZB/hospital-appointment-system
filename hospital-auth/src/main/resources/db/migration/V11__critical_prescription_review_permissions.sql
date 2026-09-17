-- ============================================================
-- auth_db V11：功能补全第二批权限 — 危急值 / 处方点评
-- 说明：技师上报危急值、医生复核、药师点评
-- 幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

-- 1. 权限种子
INSERT INTO `permission` (`perm_code`, `perm_name`, `perm_type`, `parent_id`, `path`, `sort_order`, `status`)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:medsupply:critical:report'   AS perm_code, '危急值上报'   AS perm_name, 3053 AS sort_order UNION ALL
    SELECT 'api:medsupply:critical:confirm'  , '危急值复核', 3054 UNION ALL
    SELECT 'api:medsupply:critical:query'    , '危急值查询', 3055 UNION ALL
    SELECT 'api:medsupply:prescription-review:create', '处方点评', 3023 UNION ALL
    SELECT 'api:medsupply:prescription-review:query' , '点评查询', 3024
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.perm_code = tmp.perm_code);

-- 2. 角色绑定
-- 2.1 ROLE_LAB_TECH / ROLE_EXAM_TECH：危急值上报/查询
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:medsupply:critical:report', 'api:medsupply:critical:query'
)
WHERE r.role_code IN ('ROLE_LAB_TECH', 'ROLE_EXAM_TECH')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 2.2 ROLE_DOCTOR / ROLE_DEPT_CHIEF：危急值复核/查询（+ 处方点评查询）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:medsupply:critical:confirm', 'api:medsupply:critical:query',
    'api:medsupply:prescription-review:query'
)
WHERE r.role_code IN ('ROLE_DOCTOR', 'ROLE_DEPT_CHIEF')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 2.3 ROLE_PHARMACIST：处方点评 + 危急值查询
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:medsupply:critical:query',
    'api:medsupply:prescription-review:create', 'api:medsupply:prescription-review:query'
)
WHERE r.role_code = 'ROLE_PHARMACIST'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 2.4 ROLE_ADMIN：全部危急值/点评权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r JOIN `permission` p ON p.perm_code IN (
    'api:medsupply:critical:report', 'api:medsupply:critical:confirm', 'api:medsupply:critical:query',
    'api:medsupply:prescription-review:create', 'api:medsupply:prescription-review:query'
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
