-- ============================================================
-- auth_db V17：手术/麻醉中心权限（迭代10 F1~F5）
-- 说明：
--   1. 新增 surgery 中心业务权限码 10 个（前缀 api:inpatient:surgery:*）：
--      手术中心看板 / 手术中心列表 / 门诊手术创建 / 手术排台(统一手术单) /
--      术前评估提交 / 知情同意签署 / 手术开始 / 手术记录录入 / 麻醉记录录入 / 术后随访触发
--      注：排台用 center:schedule 与 V12 既有 api:inpatient:surgery:schedule
--      （surgery_apply 申请单排台）区分，二者并存。
--   2. 角色绑定：管理员/超管/医生全量；手术中心看板与列表另绑护士
--      （ROLE_NURSE，V6 迁移已创建）。
--   幂等：INSERT ... SELECT + NOT EXISTS（重复执行不产生重复行）
-- ============================================================

-- 1. 权限种子
INSERT INTO permission (perm_code, perm_name, perm_type, parent_id, path, sort_order, status)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:inpatient:surgery:board'          AS perm_code, '手术中心看板查看'   AS perm_name, 2900 AS sort_order FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:list'          , '手术中心列表查询',    2901 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:outpatient'    , '门诊手术创建',        2902 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:center:schedule', '手术排台(统一手术单)', 2903 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:preop'         , '术前评估提交',        2904 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:consent'       , '知情同意签署',        2905 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:start'         , '手术开始',            2906 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:record'        , '手术记录录入',        2907 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:anesthesia'    , '麻醉记录录入',        2908 FROM dual UNION ALL
    SELECT 'api:inpatient:surgery:postop-followup', '术后随访触发',       2909 FROM dual
) tmp
WHERE NOT EXISTS (SELECT 1 FROM permission p WHERE p.perm_code = tmp.perm_code);

-- 2. 管理员/超管/医生：全量
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:surgery:board', 'api:inpatient:surgery:list',
    'api:inpatient:surgery:outpatient', 'api:inpatient:surgery:center:schedule',
    'api:inpatient:surgery:preop', 'api:inpatient:surgery:consent',
    'api:inpatient:surgery:start', 'api:inpatient:surgery:record',
    'api:inpatient:surgery:anesthesia', 'api:inpatient:surgery:postop-followup'
)
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_DOCTOR')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3. 护士：手术中心看板、手术中心列表
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.perm_code IN (
    'api:inpatient:surgery:board', 'api:inpatient:surgery:list'
)
WHERE r.role_code = 'ROLE_NURSE'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
