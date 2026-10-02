-- =====================================================================
-- V22: 迭代14 急诊与后台运营权限码（auth）
-- 幂等：INSERT...SELECT + NOT EXISTS
-- =====================================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:clinic:triage:manage', '急诊分诊管理', 'api', NULL, NULL, 3130, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:clinic:triage:manage');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:clinic:rescue:manage', '抢救记录管理', 'api', NULL, NULL, 3131, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:clinic:rescue:manage');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:clinic:attendance:manage', '考勤管理', 'api', NULL, NULL, 3132, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:clinic:attendance:manage');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:medsupply:consumable:manage', '耗材管理', 'api', NULL, NULL, 3133, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:medsupply:consumable:manage');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:medsupply:equipment:manage', '设备管理', 'api', NULL, NULL, 3134, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:medsupply:equipment:manage');

-- 角色绑定：ADMIN/SUPER_ADMIN 全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code IN (
  'api:clinic:triage:manage', 'api:clinic:rescue:manage', 'api:clinic:attendance:manage',
  'api:medsupply:consumable:manage', 'api:medsupply:equipment:manage')
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- DOCTOR 绑定急诊相关
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code IN (
  'api:clinic:triage:manage', 'api:clinic:rescue:manage')
WHERE r.role_code = 'ROLE_DOCTOR'
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- NURSE 绑定分诊
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code = 'api:clinic:triage:manage'
WHERE r.role_code = 'ROLE_NURSE'
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
