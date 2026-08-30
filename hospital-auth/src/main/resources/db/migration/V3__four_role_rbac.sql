-- ============================================================
-- V3：四角色权限体系改造
-- 说明：
--   1. 新增 ROLE_SUPER_ADMIN（超级管理员，预置唯一）
--   2. 将原 ROLE_ADMIN 从「平台超级管理员」降级为「管理员」
--   3. 将预置账号 13800000000 的角色关联从 ROLE_ADMIN 迁移到 ROLE_SUPER_ADMIN
--   4. 新增预置示例管理员账号（供管理员角色验收，密码 123456）
-- ============================================================

-- 1. 新增超级管理员角色
INSERT INTO `role` (`role_code`, `role_name`, `description`, `status`) VALUES
('ROLE_SUPER_ADMIN', '超级管理员', '平台超级管理员，唯一预置，拥有最高权限，不可新增/删除', 1);

-- 2. 降级 ROLE_ADMIN 描述（从「平台超级管理员」改为「管理员」）
UPDATE `role`
SET `role_name` = '管理员',
    `description` = '平台管理员，负责科室/医生/排班/药品/用户等业务管理，不可操纵管理员角色',
    `update_time` = NOW()
WHERE `role_code` = 'ROLE_ADMIN';

-- 3. 将预置账号 13800000000 的角色从 ROLE_ADMIN 迁移到 ROLE_SUPER_ADMIN
--    （先删旧关联，再插入新关联，保持唯一）
DELETE FROM `user_role`
WHERE `user_id` = (SELECT `id` FROM `user` WHERE `phone` = '13800000000' LIMIT 1)
  AND `role_id` = (SELECT `id` FROM `role` WHERE `role_code` = 'ROLE_ADMIN' LIMIT 1);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT `u`.`id`, `r`.`id`
FROM `user` `u`
JOIN `role` `r` ON `r`.`role_code` = 'ROLE_SUPER_ADMIN'
WHERE `u`.`phone` = '13800000000'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` `ur`
      WHERE `ur`.`user_id` = `u`.`id` AND `ur`.`role_id` = `r`.`id`
  );

-- 4. 预置示例管理员账号（密码 123456 的 BCrypt 哈希），仅 ROLE_ADMIN 角色
INSERT INTO `user` (`phone`, `password`, `real_name`, `gender`, `user_type`, `status`, `need_password_change`) VALUES
('13900000000', '$2a$10$hmW8QM57CDTzmXO4LTvJQOSrjdGqzP7sIobHB7PDWGoAYu6Mr9sW.', '平台管理员', 0, 'ADMIN', 1, 1);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT `u`.`id`, `r`.`id`
FROM `user` `u`
JOIN `role` `r` ON `r`.`role_code` = 'ROLE_ADMIN'
WHERE `u`.`phone` = '13900000000'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` `ur`
      WHERE `ur`.`user_id` = `u`.`id` AND `ur`.`role_id` = `r`.`id`
  );
