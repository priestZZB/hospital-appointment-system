-- ============================================================
-- auth_db V14：药事管理权限（迭代7 B1~B11）
-- 幂等：INSERT ... SELECT + NOT EXISTS
-- ============================================================

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:medsupply:drug:type:manage'     AS perm_code, '药品分类管理'     AS perm_name, 2600 AS sort_order FROM dual UNION ALL
    SELECT 'api:medsupply:drug:batch:list'      , '批次效期查询'   , 2601 FROM dual UNION ALL
    SELECT 'api:medsupply:drug:batch:create'    , '采购入库批次'   , 2602 FROM dual UNION ALL
    SELECT 'api:medsupply:drug:scrap'           , '养护报损'       , 2603 FROM dual UNION ALL
    SELECT 'api:medsupply:drug:return:create'   , '退药冲账'       , 2604 FROM dual UNION ALL
    SELECT 'api:medsupply:drug:transfer:create' , '药品调拨'       , 2605 FROM dual UNION ALL
    SELECT 'api:medsupply:narcotic:register'    , '麻精五专登记'   , 2606 FROM dual UNION ALL
    SELECT 'api:medsupply:narcotic:query'       , '麻精登记查询'   , 2607 FROM dual UNION ALL
    SELECT 'api:medsupply:decoction:create'     , '代煎下单'       , 2608 FROM dual UNION ALL
    SELECT 'api:medsupply:decoction:manage'     , '代煎状态流转'   , 2609 FROM dual UNION ALL
    SELECT 'api:medsupply:decoction:query'      , '代煎查询'       , 2610 FROM dual UNION ALL
    SELECT 'api:medsupply:drugrule:manage'      , 'CDSS规则管理'   , 2611 FROM dual UNION ALL
    SELECT 'api:medsupply:antibiotic:auth'      , '抗菌授权管理'   , 2612 FROM dual UNION ALL
    SELECT 'api:medsupply:guidance:print'       , '用药指导单打印' , 2613 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'MENU', 0, tmp.path, tmp.sort_order, 1
FROM (
    SELECT 'menu:admin:drug-batch' AS perm_code, '药库批次效期' AS perm_name, '/drugs' AS path, 51 AS sort_order FROM dual UNION ALL
    SELECT 'menu:admin:narcotic'   , '麻精登记'   , '/drugs', 52 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 药师：药事全量（批次/退药/调拨/麻精/代煎流转/CDSS规则/指导单）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:drug:type:manage', 'api:medsupply:drug:batch:list', 'api:medsupply:drug:batch:create',
    'api:medsupply:drug:scrap', 'api:medsupply:drug:return:create', 'api:medsupply:drug:transfer:create',
    'api:medsupply:narcotic:register', 'api:medsupply:narcotic:query',
    'api:medsupply:decoction:create', 'api:medsupply:decoction:manage', 'api:medsupply:decoction:query',
    'api:medsupply:drugrule:manage', 'api:medsupply:guidance:print',
    'menu:admin:drug-batch', 'menu:admin:narcotic'
)
WHERE r.role_code = 'ROLE_PHARMACIST'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 管理员/超管：全量药事权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:drug:type:manage', 'api:medsupply:drug:batch:list', 'api:medsupply:drug:batch:create',
    'api:medsupply:drug:scrap', 'api:medsupply:drug:return:create', 'api:medsupply:drug:transfer:create',
    'api:medsupply:narcotic:register', 'api:medsupply:narcotic:query',
    'api:medsupply:decoction:create', 'api:medsupply:decoction:manage', 'api:medsupply:decoction:query',
    'api:medsupply:drugrule:manage', 'api:medsupply:antibiotic:auth', 'api:medsupply:guidance:print',
    'menu:admin:drug-batch', 'menu:admin:narcotic'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 医生：代煎下单/查询、指导单、抗菌授权查询（开方时校验走内部逻辑）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:medsupply:decoction:create', 'api:medsupply:decoction:query', 'api:medsupply:guidance:print'
)
WHERE r.role_code = 'ROLE_DOCTOR'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 患者：本人代煎查询与取药凭证
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN ('api:medsupply:decoction:query')
WHERE r.role_code = 'ROLE_PATIENT'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
