-- ============================================================
-- V9：数据范围跨科室申请 data_scope_apply（迭代 5 阶段 3）
-- 说明：
--   1. 新建 data_scope_apply 表：用户提交跨科室数据访问申请 → 科主任/管理员/超管审批。
--   2. 数据范围默认规则（不落表，由代码 DataScopeUtil 解析）：
--      超管/管理员=全量；收费员/分诊护士=全院；科主任=本科室；医师=本人病历/处方；
--      影像/检验技师=本科室申请单；护士=本输液室；患者=本人。
--   3. 审批通过后给用户临时/长期跨科室授权（授权记录即本表 APPROVED 行）。
--   4. 权限码：api:auth:data-scope:apply（申请，绑定业务角色）、
--      api:auth:data-scope:approve（审批，绑定超管/管理员/科主任）、
--      api:auth:data-scope:list（本人申请列表，全部登录用户）。
-- 幂等：全部使用 IF NOT EXISTS / INSERT ... SELECT + NOT EXISTS。
-- ============================================================

-- ==================== 1. data_scope_apply 表 ====================
CREATE TABLE IF NOT EXISTS `data_scope_apply` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`                BIGINT       NOT NULL                COMMENT '申请人ID（auth.user.id）',
    `user_name`              VARCHAR(50)  NOT NULL                COMMENT '申请人姓名（冗余）',
    `target_department_id`   BIGINT       NOT NULL                COMMENT '目标科室ID（clinic.department.id）',
    `target_department_name` VARCHAR(100) DEFAULT NULL            COMMENT '目标科室名称（冗余）',
    `reason`                 VARCHAR(500) NOT NULL                COMMENT '申请理由',
    `status`                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待审批 / APPROVED-已通过 / REJECTED-已驳回',
    `approver_id`            BIGINT       DEFAULT NULL            COMMENT '审批人ID',
    `approver_name`          VARCHAR(50)  DEFAULT NULL            COMMENT '审批人姓名',
    `approve_comment`        VARCHAR(500) DEFAULT NULL            COMMENT '审批意见',
    `expire_time`            DATETIME     DEFAULT NULL            COMMENT '授权过期时间（NULL=长期）',
    `apply_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `approve_time`           DATETIME     DEFAULT NULL            COMMENT '审批时间',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dsa_user_status` (`user_id`, `status`),
    KEY `idx_dsa_dept_status` (`target_department_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='跨科室数据范围申请表';

-- ==================== 2. 数据范围权限码 ====================
INSERT INTO `permission` (`perm_code`, `perm_name`, `perm_type`, `parent_id`, `path`, `sort_order`, `status`)
SELECT tmp.perm_code, tmp.perm_name, 'API', 0, NULL, tmp.sort_order, 1
FROM (
    SELECT 'api:auth:data-scope:apply'    AS perm_code, '提交跨科室申请' AS perm_name, 1050 AS sort_order UNION ALL
    SELECT 'api:auth:data-scope:approve'  , '审批跨科室申请', 1051 UNION ALL
    SELECT 'api:auth:data-scope:list'     , '跨科室申请列表', 1052
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.perm_code = tmp.perm_code);

-- ==================== 3. 权限绑定 ====================
-- 3.1 申请：业务角色（科主任/医师/影像技师/检验技师/护士/分诊护士/药师）+ 超管/管理员（管理员可代提）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.perm_code = 'api:auth:data-scope:apply'
WHERE r.role_code IN ('ROLE_DEPT_CHIEF','ROLE_DOCTOR','ROLE_EXAM_TECH','ROLE_LAB_TECH',
                      'ROLE_NURSE','ROLE_TRIAGE_NURSE','ROLE_PHARMACIST','ROLE_SUPER_ADMIN','ROLE_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3.2 审批：超管/管理员/科主任（科室负责人审批本科室或跨科室申请）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.perm_code = 'api:auth:data-scope:approve'
WHERE r.role_code IN ('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_DEPT_CHIEF')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 3.3 本人列表：全部角色（含患者）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.perm_code = 'api:auth:data-scope:list'
WHERE NOT EXISTS (SELECT 1 FROM `role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);