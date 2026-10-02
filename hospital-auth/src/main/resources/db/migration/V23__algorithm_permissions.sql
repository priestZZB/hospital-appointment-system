-- =====================================================================
-- V23: 迭代15 算法层权限码（auth）
-- 幂等：INSERT...SELECT + NOT EXISTS
-- =====================================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:clinic:schedule:algo', '遗传排班算法', 'api', NULL, NULL, 3140, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:clinic:schedule:algo');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:clinic:queue:priority', '优先级叫号视图', 'api', NULL, NULL, 3141, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:clinic:queue:priority');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:clinic:stop:reschedule', '停诊重调度', 'api', NULL, NULL, 3142, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:clinic:stop:reschedule');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:ai:noshow:view', '爽约预测视图', 'api', NULL, NULL, 3143, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:ai:noshow:view');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:inpatient:bedplan:manage', '排床优化管理', 'api', NULL, NULL, 3144, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:inpatient:bedplan:manage');

-- 角色绑定：ADMIN/SUPER_ADMIN 全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code IN (
  'api:clinic:schedule:algo', 'api:clinic:queue:priority', 'api:clinic:stop:reschedule',
  'api:ai:noshow:view', 'api:inpatient:bedplan:manage')
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- DOCTOR 绑定叫号优先级与爽约预测
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code IN (
  'api:clinic:queue:priority', 'api:ai:noshow:view')
WHERE r.role_code = 'ROLE_DOCTOR'
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
