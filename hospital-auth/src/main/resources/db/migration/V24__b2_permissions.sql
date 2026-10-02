-- =====================================================================
-- V24: 迭代16 AI智能层权限码（auth）
-- 幂等：INSERT...SELECT + NOT EXISTS
-- =====================================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:ai:consult:manage', 'AI多轮问诊', 'api', NULL, NULL, 3145, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:ai:consult:manage');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:ai:predict:view', 'AI预测视图', 'api', NULL, NULL, 3146, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:ai:predict:view');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:ai:drug:recommend', 'AI用药推荐', 'api', NULL, NULL, 3147, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:ai:drug:recommend');

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT 'api:ai:summary:manage', 'AI报告摘要', 'api', NULL, NULL, 3148, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE perm_code = 'api:ai:summary:manage');

-- 角色绑定：ADMIN/SUPER_ADMIN 全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code IN (
  'api:ai:consult:manage', 'api:ai:predict:view', 'api:ai:drug:recommend', 'api:ai:summary:manage')
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- DOCTOR 绑定 AI 用药与摘要
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.perm_code IN (
  'api:ai:consult:manage', 'api:ai:drug:recommend', 'api:ai:summary:manage')
WHERE r.role_code = 'ROLE_DOCTOR'
AND NOT EXISTS (
  SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
