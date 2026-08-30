-- ============================================================
-- V8：岗位表 position + user.position_id + 岗位种子 + 岗位权限绑定（迭代 5 阶段 2）
-- 说明：
--   1. 新建 position 表（岗位 = 部门 + 职务，纯展示、不参与权限）
--   2. user 表新增 position_id（一个主岗位）
--   3. 灌岗位种子（对齐 14 临床科室 + 7 行政/医技部门）
--   4. 岗位权限码 api:auth:position:* 绑定给超管（唯一可管岗位）
--   5. 给预置测试账号分配岗位（个人信息/工作台顶栏展示用）
-- 幂等：全部使用 INSERT ... SELECT + NOT EXISTS / IF NOT EXISTS
-- ============================================================

-- ==================== 1. position 表 ====================
CREATE TABLE IF NOT EXISTS `position` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `position_code` VARCHAR(50)  NOT NULL                COMMENT '岗位编码',
    `position_name` VARCHAR(100) NOT NULL                COMMENT '岗位名称',
    `department_id` BIGINT       NOT NULL                COMMENT '所属部门ID（clinic.department.id，应用层引用）',
    `department_name` VARCHAR(100) DEFAULT NULL          COMMENT '所属部门名称冗余（跨库展示用）',
    `title`         VARCHAR(50)  NOT NULL                COMMENT '职务：科主任/主治医师/护师/药师/收费员/管理员等',
    `description`   VARCHAR(255) DEFAULT NULL            COMMENT '岗位描述',
    `status`        TINYINT      DEFAULT 1               COMMENT '1-启用 0-停用',
    `sort_order`    INT          DEFAULT 0               COMMENT '排序号',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_position_code` (`position_code`),
    KEY `idx_position_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位表（部门+职务，纯展示不参与权限）';

-- ==================== 2. user 表加 position_id ====================
SET @has_position := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'position_id'
);
SET @ddl := IF(@has_position = 0,
    'ALTER TABLE `user` ADD COLUMN `position_id` BIGINT DEFAULT NULL COMMENT ''主岗位ID（position.id，纯展示）'' AFTER `user_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== 3. 岗位种子（对齐 14 临床科室 + 7 行政/医技部门） ====================
-- 临床科室岗位（department_id 对齐 clinic.department.id 1~14）
INSERT INTO `position` (`position_code`, `position_name`, `department_id`, `department_name`, `title`, `description`, `status`, `sort_order`)
SELECT tmp.position_code, tmp.position_name, tmp.department_id, tmp.department_name, tmp.title, tmp.description, 1, tmp.sort_order
FROM (
    SELECT 'POS_1_CHIEF'   AS position_code, '内科·科主任' AS position_name, 1 AS department_id, '内科' AS department_name, '科主任' AS title, '内科科室负责人' AS description, 10 AS sort_order UNION ALL
    SELECT 'POS_1_DOCTOR'  , '内科·医师', 1, '内科', '主治医师', '内科门诊医生', 11 UNION ALL
    SELECT 'POS_2_CHIEF'   , '外科·科主任', 2, '外科', '科主任', '外科科室负责人', 20 UNION ALL
    SELECT 'POS_2_DOCTOR'  , '外科·医师', 2, '外科', '主治医师', '外科门诊医生', 21 UNION ALL
    SELECT 'POS_3_CHIEF'   , '儿科·科主任', 3, '儿科', '科主任', '儿科科室负责人', 30 UNION ALL
    SELECT 'POS_3_DOCTOR'  , '儿科·医师', 3, '儿科', '主治医师', '儿科门诊医生', 31 UNION ALL
    SELECT 'POS_4_CHIEF'   , '妇产科·科主任', 4, '妇产科', '科主任', '妇产科科室负责人', 40 UNION ALL
    SELECT 'POS_4_DOCTOR'  , '妇产科·医师', 4, '妇产科', '主治医师', '妇产科门诊医生', 41 UNION ALL
    SELECT 'POS_5_CHIEF'   , '骨科·科主任', 5, '骨科', '科主任', '骨科科室负责人', 50 UNION ALL
    SELECT 'POS_5_DOCTOR'  , '骨科·医师', 5, '骨科', '主治医师', '骨科门诊医生', 51 UNION ALL
    SELECT 'POS_6_CHIEF'   , '眼科·科主任', 6, '眼科', '科主任', '眼科科室负责人', 60 UNION ALL
    SELECT 'POS_6_DOCTOR'  , '眼科·医师', 6, '眼科', '主治医师', '眼科门诊医生', 61 UNION ALL
    SELECT 'POS_7_CHIEF'   , '耳鼻喉科·科主任', 7, '耳鼻喉科', '科主任', '耳鼻喉科科室负责人', 70 UNION ALL
    SELECT 'POS_7_DOCTOR'  , '耳鼻喉科·医师', 7, '耳鼻喉科', '主治医师', '耳鼻喉科门诊医生', 71 UNION ALL
    SELECT 'POS_8_CHIEF'   , '皮肤科·科主任', 8, '皮肤科', '科主任', '皮肤科科室负责人', 80 UNION ALL
    SELECT 'POS_8_DOCTOR'  , '皮肤科·医师', 8, '皮肤科', '主治医师', '皮肤科门诊医生', 81 UNION ALL
    SELECT 'POS_9_CHIEF'   , '神经内科·科主任', 9, '神经内科', '科主任', '神经内科科室负责人', 90 UNION ALL
    SELECT 'POS_9_DOCTOR'  , '神经内科·医师', 9, '神经内科', '主治医师', '神经内科门诊医生', 91 UNION ALL
    SELECT 'POS_10_CHIEF'  , '心内科·科主任', 10, '心内科', '科主任', '心内科科室负责人', 100 UNION ALL
    SELECT 'POS_10_DOCTOR' , '心内科·医师', 10, '心内科', '主治医师', '心内科门诊医生', 101 UNION ALL
    SELECT 'POS_11_CHIEF'  , '呼吸内科·科主任', 11, '呼吸内科', '科主任', '呼吸内科科室负责人', 110 UNION ALL
    SELECT 'POS_11_DOCTOR' , '呼吸内科·医师', 11, '呼吸内科', '主治医师', '呼吸内科门诊医生', 111 UNION ALL
    SELECT 'POS_12_CHIEF'  , '消化内科·科主任', 12, '消化内科', '科主任', '消化内科科室负责人', 120 UNION ALL
    SELECT 'POS_12_DOCTOR' , '消化内科·医师', 12, '消化内科', '主治医师', '消化内科门诊医生', 121 UNION ALL
    SELECT 'POS_13_CHIEF'  , '内分泌科·科主任', 13, '内分泌科', '科主任', '内分泌科科室负责人', 130 UNION ALL
    SELECT 'POS_13_DOCTOR' , '内分泌科·医师', 13, '内分泌科', '主治医师', '内分泌科门诊医生', 131 UNION ALL
    SELECT 'POS_14_CHIEF'  , '口腔科·科主任', 14, '口腔科', '科主任', '口腔科科室负责人', 140 UNION ALL
    SELECT 'POS_14_DOCTOR' , '口腔科·医师', 14, '口腔科', '主治医师', '口腔科门诊医生', 141 UNION ALL
    -- 行政/医技部门岗位（department_id 对齐 clinic 7 个新部门 id 15~21）
    SELECT 'POS_15_MANAGER' , '信息科·科主任', 15, '信息科', '科主任', '信息科负责人（超级管理员）', 150 UNION ALL
    SELECT 'POS_15_ADMIN'   , '信息科·管理员', 15, '信息科', '管理员', '信息科系统管理员', 151 UNION ALL
    SELECT 'POS_16_CASHIER' , '收费处·收费员', 16, '收费处', '收费员', '收费处收费员', 160 UNION ALL
    SELECT 'POS_17_PHARMACIST', '药房·药师', 17, '药房', '药师', '药房药师', 170 UNION ALL
    SELECT 'POS_18_TECH'    , '影像科·技师', 18, '影像科', '技师', '影像科技师', 180 UNION ALL
    SELECT 'POS_19_TECH'    , '检验科·技师', 19, '检验科', '技师', '检验科技师', 190 UNION ALL
    SELECT 'POS_20_TRIAGE'  , '门诊部·分诊护士', 20, '门诊部', '分诊护士', '门诊部分诊护士', 200 UNION ALL
    SELECT 'POS_21_NURSE'   , '输液室·护士', 21, '输液室', '护士', '输液室护士', 210
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `position` p WHERE p.position_code = tmp.position_code);

-- ==================== 4. 岗位权限码插入 + 绑定 ====================
-- 4.1 先插入岗位权限码（V7 未含，补充进 permission 表）
INSERT INTO `permission` (`perm_code`, `perm_name`, `perm_type`, `parent_id`, `path`, `sort_order`, `status`)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:auth:position:list'   AS perm_code, '查询岗位' AS perm_name, 1040 AS sort_order UNION ALL
    SELECT 'api:auth:position:create' , '创建岗位', 1041 UNION ALL
    SELECT 'api:auth:position:update' , '编辑岗位', 1042 UNION ALL
    SELECT 'api:auth:position:delete' , '删除岗位', 1043 UNION ALL
    SELECT 'api:auth:position:assign' , '分配岗位', 1044
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.perm_code = tmp.perm_code);

-- 4.2 超管：岗位全部权限（唯一可增删改岗位）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.perm_code IN (
    'api:auth:position:list', 'api:auth:position:create', 'api:auth:position:update',
    'api:auth:position:delete', 'api:auth:position:assign'
)
WHERE r.role_code = 'ROLE_SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 4.3 管理员：可查看岗位 + 给非管理员分配岗位（决策 10），不可增删改
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.perm_code IN ('api:auth:position:list', 'api:auth:position:assign')
WHERE r.role_code = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ==================== 5. 给预置账号分配岗位 ====================
-- 13800000000 超管 → 信息科·科主任；13900000000 管理员 → 信息科·管理员
UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_15_MANAGER'
SET u.position_id = p.id
WHERE u.phone = '13800000000' AND u.position_id IS NULL;

UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_15_ADMIN'
SET u.position_id = p.id
WHERE u.phone = '13900000000' AND u.position_id IS NULL;

-- 13800000001 科主任 → 内科·科主任；13800000002 药师 → 药房·药师
UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_1_CHIEF'
SET u.position_id = p.id
WHERE u.phone = '13800000001' AND u.position_id IS NULL;

UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_17_PHARMACIST'
SET u.position_id = p.id
WHERE u.phone = '13800000002' AND u.position_id IS NULL;

-- 13800000003 影像技师 → 影像科·技师；13800000004 护士 → 输液室·护士
UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_18_TECH'
SET u.position_id = p.id
WHERE u.phone = '13800000003' AND u.position_id IS NULL;

UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_21_NURSE'
SET u.position_id = p.id
WHERE u.phone = '13800000004' AND u.position_id IS NULL;

-- 13800000005 收费员 → 收费处·收费员；13800000006 分诊 → 门诊部·分诊护士；13800000007 检验 → 检验科·技师
UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_16_CASHIER'
SET u.position_id = p.id
WHERE u.phone = '13800000005' AND u.position_id IS NULL;

UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_20_TRIAGE'
SET u.position_id = p.id
WHERE u.phone = '13800000006' AND u.position_id IS NULL;

UPDATE `user` u
JOIN `position` p ON p.position_code = 'POS_19_TECH'
SET u.position_id = p.id
WHERE u.phone = '13800000007' AND u.position_id IS NULL;