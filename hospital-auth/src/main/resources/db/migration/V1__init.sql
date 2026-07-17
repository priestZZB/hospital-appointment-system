-- ============================================================
-- auth_db V1__init.sql
-- 认证授权中心：6 张表 + 初始数据（3 角色 + 1 管理员 + 1 关联）
-- ============================================================

-- ==================== 1. user（用户表） ====================
CREATE TABLE `user` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`                 VARCHAR(20)  NOT NULL                COMMENT '手机号（登录账号）',
    `password`              VARCHAR(255) NOT NULL                COMMENT 'BCrypt 加密密码',
    `real_name`             VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    `gender`                TINYINT      DEFAULT 0               COMMENT '0-未知 1-男 2-女',
    `user_type`             VARCHAR(20)  NOT NULL                COMMENT 'PATIENT-患者 / ADMIN-管理员',
    `status`                TINYINT      DEFAULT 1               COMMENT '1-启用 0-停用',
    `last_login_time`       DATETIME     DEFAULT NULL            COMMENT '最近登录时间',
    `last_login_ip`         VARCHAR(50)  DEFAULT NULL            COMMENT '最近登录 IP',
    `need_password_change`  TINYINT      DEFAULT 0               COMMENT '首次登录强制修改密码',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      DEFAULT 0               COMMENT '逻辑删除：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    KEY `idx_user_type` (`user_type`),
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ==================== 2. role（角色表） ====================
CREATE TABLE `role` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`     VARCHAR(50)  NOT NULL                COMMENT '角色编码',
    `role_name`     VARCHAR(100) NOT NULL                COMMENT '角色名称',
    `description`   VARCHAR(255) DEFAULT NULL            COMMENT '角色描述',
    `status`        TINYINT      DEFAULT 1               COMMENT '1-启用 0-停用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

-- ==================== 3. permission（权限表） ====================
CREATE TABLE `permission` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `perm_code`     VARCHAR(100) NOT NULL                COMMENT '权限标识符',
    `perm_name`     VARCHAR(100) NOT NULL                COMMENT '权限名称',
    `perm_type`     VARCHAR(20)  NOT NULL                COMMENT 'MENU-菜单 / BUTTON-按钮 / API-接口',
    `parent_id`     BIGINT       DEFAULT 0               COMMENT '父权限ID，构建菜单树',
    `path`          VARCHAR(255) DEFAULT NULL            COMMENT '前端路由路径',
    `sort_order`    INT          DEFAULT 0               COMMENT '排序号',
    `status`        TINYINT      DEFAULT 1               COMMENT '1-启用 0-停用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    KEY `idx_perm_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限表';

-- ==================== 4. user_role（用户角色关联表） ====================
CREATE TABLE `user_role` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
    `role_id`     BIGINT   NOT NULL                COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

-- ==================== 5. role_permission（角色权限关联表） ====================
CREATE TABLE `role_permission` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT   NOT NULL                COMMENT '角色ID',
    `permission_id` BIGINT   NOT NULL                COMMENT '权限ID',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色权限关联表';

-- ==================== 6. audit_log（操作审计日志表） ====================
CREATE TABLE `audit_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       DEFAULT NULL            COMMENT '操作人ID',
    `username`        VARCHAR(50)  DEFAULT NULL            COMMENT '操作人用户名',
    `operation`       VARCHAR(100) NOT NULL                COMMENT '操作类型描述',
    `http_method`     VARCHAR(10)  DEFAULT NULL            COMMENT 'HTTP 方法',
    `request_uri`     VARCHAR(255) DEFAULT NULL            COMMENT '请求 URI',
    `request_ip`      VARCHAR(50)  DEFAULT NULL            COMMENT '请求来源 IP',
    `request_params`  TEXT         DEFAULT NULL            COMMENT '请求参数（截断至 2000 字符）',
    `response_result` TEXT         DEFAULT NULL            COMMENT '响应结果（截断至 2000 字符）',
    `execution_time`  BIGINT       DEFAULT NULL            COMMENT '执行耗时（毫秒）',
    `status`          TINYINT      DEFAULT 1               COMMENT '1-成功 0-异常',
    `error_message`   VARCHAR(500) DEFAULT NULL            COMMENT '异常信息',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_user_id` (`user_id`),
    KEY `idx_audit_create_time` (`create_time`),
    KEY `idx_audit_operation` (`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志表';

-- ==================== 初始数据 ====================

-- 预置角色
INSERT INTO `role` (`role_code`, `role_name`, `description`) VALUES
('ROLE_ADMIN',   '管理员', '平台超级管理员，拥有全部权限'),
('ROLE_DOCTOR',  '医生',   '门诊医生，负责接诊与叫号'),
('ROLE_PATIENT', '患者',   '患者用户，通过小程序挂号就诊');

-- 预置管理员账户（密码为 "admin123" 的 BCrypt 哈希）
INSERT INTO `user` (`phone`, `password`, `real_name`, `gender`, `user_type`, `status`, `need_password_change`) VALUES
('13800000000', '$2a$12$G9OpjHlTW2dTR/vomvrsQ.BjTtUT3MjI6ordDptw/DnPG0rGzmMna', '系统管理员', 0, 'ADMIN', 1, 1);

-- 预置用户角色关联（管理员拥有 ADMIN 角色）
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1);
