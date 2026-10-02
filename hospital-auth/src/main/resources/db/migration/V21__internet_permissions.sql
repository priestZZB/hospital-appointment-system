-- ============================================================
-- auth_db V21：互联网医院权限码（迭代13 L3/K1/K2/K3）
--   sort 3120~3123；ROLE_ADMIN/ROLE_SUPER_ADMIN 全量；医生绑复诊接诊
--   幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

-- 1. 权限种子
INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:clinic:notice:manage'      AS perm_code, '公告管理'       AS perm_name, 3120 AS sort_order FROM dual UNION ALL
    SELECT 'api:clinic:evaluation:view'    , '评价查看'                   , 3121 FROM dual UNION ALL
    SELECT 'api:clinic:consult:handle'     , '图文复诊接诊'               , 3122 FROM dual UNION ALL
    SELECT 'api:medsupply:delivery:manage' , '购药配送管理'               , 3123 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 2. 管理员/超管：全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:notice:manage', 'api:clinic:evaluation:view',
    'api:clinic:consult:handle', 'api:medsupply:delivery:manage'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3. 医生：评价查看 + 复诊接诊
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:clinic:evaluation:view', 'api:clinic:consult:handle'
)
WHERE r.role_code = 'ROLE_DOCTOR'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

COMMIT;
