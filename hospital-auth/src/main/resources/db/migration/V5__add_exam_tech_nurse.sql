-- ============================================================
-- V5：八角色体系扩展（新增检查科技师、护士）（Oracle 版）
-- 说明：对齐真实医院门诊业务
--   1. 新增 ROLE_EXAM_TECH（检查科技师）：检查执行登记、报告录入
--   2. 新增 ROLE_NURSE（护士）：门诊护士站，输液执行与记录
--   3. 预置示例账号（密码均为 123456）
-- ============================================================

-- 1. 新增检查技师、护士角色（19c 不支持多行 VALUES，拆为多条 INSERT）
INSERT INTO role (role_code, role_name, description, status)
VALUES ('ROLE_EXAM_TECH', '检查技师', '检查科技师，负责检查执行登记与检查报告录入', 1);

INSERT INTO role (role_code, role_name, description, status)
VALUES ('ROLE_NURSE', '护士', '门诊护士站护士，负责输液执行与输液记录', 1);

-- 2. 预置示例检查技师账号（13800000003 / 123456）
INSERT INTO sys_user (phone, password, real_name, gender, user_type, status, need_password_change)
VALUES ('13800000003', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '李技师', 0, 'ADMIN', 1, 1);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN role r ON r.role_code = 'ROLE_EXAM_TECH'
WHERE u.phone = '13800000003'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 3. 预置示例护士账号（13800000004 / 123456）
INSERT INTO sys_user (phone, password, real_name, gender, user_type, status, need_password_change)
VALUES ('13800000004', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '孙护士', 0, 'ADMIN', 1, 1);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN role r ON r.role_code = 'ROLE_NURSE'
WHERE u.phone = '13800000004'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
