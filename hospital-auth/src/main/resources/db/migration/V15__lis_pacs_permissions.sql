-- ============================================================
-- auth_db V15：检验 LIS + 影像中心权限（迭代8 C2~C4/D1~D6）
-- 幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:medsupply:specimen:collect' AS perm_code, '标本采集'     AS perm_name, 2700 AS sort_order FROM dual UNION ALL
    SELECT 'api:medsupply:specimen:receive' , '标本核收'   , 2701 FROM dual UNION ALL
    SELECT 'api:medsupply:specimen:query'   , '标本查询'   , 2702 FROM dual UNION ALL
    SELECT 'api:medsupply:result:entry'     , '检验结果录入', 2703 FROM dual UNION ALL
    SELECT 'api:medsupply:result:query'     , '检验结果查询', 2704 FROM dual UNION ALL
    SELECT 'api:medsupply:labreport:print'  , '化验单打印' , 2705 FROM dual UNION ALL
    SELECT 'api:medsupply:examresv:book'    , '检查预约'   , 2706 FROM dual UNION ALL
    SELECT 'api:medsupply:examresv:checkin' , '检查报到'   , 2707 FROM dual UNION ALL
    SELECT 'api:medsupply:examresv:query'   , '检查预约查询', 2708 FROM dual UNION ALL
    SELECT 'api:medsupply:image:upload'     , '影像上传'   , 2709 FROM dual UNION ALL
    SELECT 'api:medsupply:image:query'      , '影像查询'   , 2710 FROM dual UNION ALL
    SELECT 'api:medsupply:template:manage'  , '报告模板管理', 2711 FROM dual UNION ALL
    SELECT 'api:medsupply:cloudlink:create' , '云影像链接生成', 2712 FROM dual UNION ALL
    SELECT 'api:medsupply:cloudlink:view'   , '云影像查看' , 2713 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'MENU', 0, tmp.path, tmp.sort_order, 1
FROM (
    SELECT 'menu:admin:exam-workbench' AS perm_code, '医技工作台' AS perm_name, '/exam-tech' AS path, 53 AS sort_order FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 检验技师：标本/结果/化验单
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:specimen:collect', 'api:medsupply:specimen:receive', 'api:medsupply:specimen:query',
    'api:medsupply:result:entry', 'api:medsupply:result:query', 'api:medsupply:labreport:print',
    'api:medsupply:image:query', 'menu:admin:exam-workbench'
)
WHERE r.role_code = 'ROLE_LAB_TECH'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 检查技师：预约/报到/影像上传/模板/云影像
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:examresv:book', 'api:medsupply:examresv:checkin', 'api:medsupply:examresv:query',
    'api:medsupply:image:upload', 'api:medsupply:image:query',
    'api:medsupply:template:manage', 'api:medsupply:cloudlink:create', 'api:medsupply:cloudlink:view',
    'menu:admin:exam-workbench'
)
WHERE r.role_code = 'ROLE_EXAM_TECH'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 管理员/超管/医生：全量（医生可查结果/看影像/开预约）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:specimen:collect', 'api:medsupply:specimen:receive', 'api:medsupply:specimen:query',
    'api:medsupply:result:entry', 'api:medsupply:result:query', 'api:medsupply:labreport:print',
    'api:medsupply:examresv:book', 'api:medsupply:examresv:checkin', 'api:medsupply:examresv:query',
    'api:medsupply:image:upload', 'api:medsupply:image:query',
    'api:medsupply:template:manage', 'api:medsupply:cloudlink:create', 'api:medsupply:cloudlink:view',
    'menu:admin:exam-workbench'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_DOCTOR')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 患者：本人预约查询、云影像查看
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:examresv:query', 'api:medsupply:cloudlink:view', 'api:medsupply:result:query'
)
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
