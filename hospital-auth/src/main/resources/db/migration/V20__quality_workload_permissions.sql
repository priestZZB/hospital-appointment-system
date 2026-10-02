-- ============================================================
-- auth_db V20：迭代12补全 J2/J4 权限码
--   质控指标看板（api:medsupply:quality:view，sort 3110）
--   医生工作量统计（api:clinic:stats:workload，sort 3111）
--   角色绑定：ROLE_ADMIN/ROLE_SUPER_ADMIN/ROLE_DOCTOR 全量
--   幂等：INSERT ... SELECT + NOT EXISTS（重复执行不产生重复行）
-- ============================================================

-- 1. 权限种子
INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:medsupply:quality:view' AS perm_code, '质控指标看板' AS perm_name, 3110 AS sort_order FROM dual UNION ALL
    SELECT 'api:clinic:stats:workload'  , '医生工作量统计',     3111 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 2. 管理员/超管/医生：全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:quality:view', 'api:clinic:stats:workload'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_DOCTOR')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

COMMIT;
