-- ============================================================
-- V2：补齐预置医生种子数据
-- 说明：V1 只建了 doctor 表但未插入种子数据，导致全新部署后
--       医生管理/叫号/接诊等依赖 doctor.id=1（李医生，内科）的
--       功能无法使用。此处按文档约定补齐（幂等，重复执行安全）。
-- ============================================================
INSERT INTO `doctor` (`id`, `user_id`, `name`, `gender`, `phone`, `department_id`,
                      `title`, `specialty`, `introduction`, `status`, `create_time`, `update_time`)
SELECT 1, 1, '李医生', 1, '13800000000', 1,
       'CHIEF', '内科', '内科主任医师', 1, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `doctor` WHERE `id` = 1);
