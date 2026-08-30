-- ============================================================
-- V4：六角色体系扩展（新增科主任、药师）
-- 说明：对齐真实医院业务
--   1. 新增 ROLE_DEPT_CHIEF（科主任）：本科室排班上报、医生管理、停诊初审
--   2. 新增 ROLE_PHARMACIST（药师）：处方审核、发药
--   3. 预置示例账号（密码均为 123456）
-- ============================================================

-- 1. 新增科主任、药师角色
INSERT INTO `role` (`role_code`, `role_name`, `description`, `status`) VALUES
('ROLE_DEPT_CHIEF', '科主任', '科室负责人，负责本科室排班上报、医生管理、停诊初审', 1),
('ROLE_PHARMACIST', '药师', '药房药师，负责处方审核与发药', 1);

-- 2. 预置示例科主任账号（13800000001 / 123456），ROLE_DEPT_CHIEF + ROLE_DOCTOR
--    （科主任本身也是医生，可出诊接诊）
INSERT INTO `user` (`phone`, `password`, `real_name`, `gender`, `user_type`, `status`, `need_password_change`) VALUES
('13800000001', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '王科主任', 0, 'DOCTOR', 1, 1);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT `u`.`id`, `r`.`id`
FROM `user` `u`
JOIN `role` `r` ON `r`.`role_code` IN ('ROLE_DEPT_CHIEF', 'ROLE_DOCTOR')
WHERE `u`.`phone` = '13800000001'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` `ur`
      WHERE `ur`.`user_id` = `u`.`id` AND `ur`.`role_id` = `r`.`id`
  );

-- 3. 预置示例药师账号（13800000002 / 123456），仅 ROLE_PHARMACIST
INSERT INTO `user` (`phone`, `password`, `real_name`, `gender`, `user_type`, `status`, `need_password_change`) VALUES
('13800000002', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '赵药师', 0, 'ADMIN', 1, 1);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT `u`.`id`, `r`.`id`
FROM `user` `u`
JOIN `role` `r` ON `r`.`role_code` = 'ROLE_PHARMACIST'
WHERE `u`.`phone` = '13800000002'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` `ur`
      WHERE `ur`.`user_id` = `u`.`id` AND `ur`.`role_id` = `r`.`id`
  );
