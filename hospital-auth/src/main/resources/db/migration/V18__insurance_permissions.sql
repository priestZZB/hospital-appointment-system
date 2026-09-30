-- ============================================================
-- auth_db V18：医保与财务权限（迭代11 医保与财务 H1~H4）
-- 说明：
--   1. 新增 payment 医保/收费权限码 8 个（前缀 api:payment:*）：
--      医保目录映射管理 / 医保目录映射查询 / 医保费用结算 / 医保结算单查询 /
--      医保票据打印 / 医保结算冲正 / 收费项目管理 / 收费项目查询
--      （第 8 个为结算冲正 reverse，冲正与结算同属敏感资金操作，独立授权）。
--   2. 角色绑定：管理员/超管全量；ROLE_DOCTOR（V1__init.sql 已创建）另绑
--      charge-item:query 与 insurance:settle:query（医生可查目录与结算单）。
--      注：按迭代 11 交付口径未给 ROLE_CASHIER 绑定，如需收费员办理医保
--      结算可后续补绑。
--   幂等：INSERT ... SELECT + NOT EXISTS（重复执行不产生重复行）
-- ============================================================

-- 1. 权限种子
INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:payment:insurance:catalog:manage' AS perm_code, '医保目录映射管理' AS perm_name, 3000 AS sort_order FROM dual UNION ALL
    SELECT 'api:payment:insurance:catalog:query' , '医保目录映射查询',   3001 FROM dual UNION ALL
    SELECT 'api:payment:insurance:settle'        , '医保费用结算',       3002 FROM dual UNION ALL
    SELECT 'api:payment:insurance:settle:query'  , '医保结算单查询',     3003 FROM dual UNION ALL
    SELECT 'api:payment:insurance:voucher'       , '医保票据打印',       3004 FROM dual UNION ALL
    SELECT 'api:payment:insurance:reverse'       , '医保结算冲正',       3005 FROM dual UNION ALL
    SELECT 'api:payment:charge-item:manage'      , '收费项目管理',       3006 FROM dual UNION ALL
    SELECT 'api:payment:charge-item:query'       , '收费项目查询',       3007 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 2. 管理员/超管：全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:payment:insurance:catalog:manage', 'api:payment:insurance:catalog:query',
    'api:payment:insurance:settle', 'api:payment:insurance:settle:query',
    'api:payment:insurance:voucher', 'api:payment:insurance:reverse',
    'api:payment:charge-item:manage', 'api:payment:charge-item:query'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3. 医生：收费项目查询 + 医保结算单查询（角色 ROLE_DOCTOR 由 V1__init.sql 创建）
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:payment:charge-item:query', 'api:payment:insurance:settle:query'
)
WHERE r.role_code = 'ROLE_DOCTOR'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
