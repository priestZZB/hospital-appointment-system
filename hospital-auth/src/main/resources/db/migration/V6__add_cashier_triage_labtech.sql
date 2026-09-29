-- ============================================================
-- V6：十一角色体系扩展（新增收费员、分诊护士、检验技师）
-- 说明：对齐真实医院门诊业务
--   1. 影像技师（原检查技师 ROLE_EXAM_TECH 更名，职责聚焦影像检查）
--   2. 新增 ROLE_CASHIER（收费员）：收费处挂号/诊疗收费、退费、日结
--   3. 新增 ROLE_TRIAGE_NURSE（分诊护士）：分诊、签到登记、排队叫号辅助
--   4. 新增 ROLE_LAB_TECH（检验技师）：检验标本执行、检验结果录入
--   5. 预置示例账号（密码均为 123456）
-- ============================================================

-- 1. 原检查技师更名为影像技师（聚焦放射/超声/内镜/心电）
UPDATE role
SET role_name = '影像技师',
    description = '影像科检查技师，负责放射/超声/内镜/心电检查执行与影像报告录入',
    update_time = SYSDATE
WHERE role_code = 'ROLE_EXAM_TECH';

-- 2. 新增收费员、分诊护士、检验技师角色
INSERT INTO role (role_code, role_name, description, status) VALUES ('ROLE_CASHIER', '收费员', '收费处收费员，负责挂号收费、诊疗收费、退费与日结对账', 1);
INSERT INTO role (role_code, role_name, description, status) VALUES ('ROLE_TRIAGE_NURSE', '分诊护士', '门诊分诊台护士，负责患者分诊、签到登记与排队叫号辅助', 1);
INSERT INTO role (role_code, role_name, description, status) VALUES ('ROLE_LAB_TECH', '检验技师', '检验科技师，负责检验标本执行与检验结果录入', 1);

-- 3. 预置示例收费员账号（13800000005 / 123456）
INSERT INTO sys_user (phone, password, real_name, gender, user_type, status, need_password_change) VALUES ('13800000005', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '钱收费员', 0, 'ADMIN', 1, 1);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN role r ON r.role_code = 'ROLE_CASHIER'
WHERE u.phone = '13800000005'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 4. 预置示例分诊护士账号（13800000006 / 123456）
INSERT INTO sys_user (phone, password, real_name, gender, user_type, status, need_password_change) VALUES ('13800000006', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '周分诊', 0, 'ADMIN', 1, 1);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN role r ON r.role_code = 'ROLE_TRIAGE_NURSE'
WHERE u.phone = '13800000006'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 5. 预置示例检验技师账号（13800000007 / 123456）
INSERT INTO sys_user (phone, password, real_name, gender, user_type, status, need_password_change) VALUES ('13800000007', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '吴检验师', 0, 'ADMIN', 1, 1);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN role r ON r.role_code = 'ROLE_LAB_TECH'
WHERE u.phone = '13800000007'
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
