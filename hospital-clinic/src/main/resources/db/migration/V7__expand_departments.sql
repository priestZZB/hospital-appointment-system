-- ============================================================
-- V7：部门扩充 — 新增 7 个行政/医技部门（迭代 5 阶段 2）
-- 说明：
--   1. 现有 14 个临床科室（id 1~14）之上，新增行政/医技部门，
--      供岗位（position.department_id）与数据范围使用。
--   2. 新部门 id 从 15 开始（V8 auth 岗位种子依赖 15~21 的 id 约定）。
--   3. 幂等：全部使用 INSERT ... SELECT + NOT EXISTS。
-- ============================================================

INSERT INTO `department` (`dept_name`, `dept_code`, `description`, `location`, `phone`, `status`, `sort_order`)
SELECT tmp.dept_name, tmp.dept_code, tmp.description, tmp.location, tmp.phone, 1, tmp.sort_order
FROM (
    SELECT '信息科'   AS dept_name, 'IT'            AS dept_code, '医院信息化系统管理维护'  AS description, '信息楼 3F' AS location, '021-1001' AS phone, 100 AS sort_order UNION ALL
    SELECT '收费处',   'CASHIER',     '门诊挂号与诊疗收费', '门诊楼 1F', '021-1002', 101 UNION ALL
    SELECT '药房',     'PHARMACY',    '门诊药房，处方审核与发药', '门诊楼 1F', '021-1003', 102 UNION ALL
    SELECT '影像科',   'RADIOLOGY',   '放射/超声/内镜/心电检查', '医技楼 2F', '021-1004', 103 UNION ALL
    SELECT '检验科',   'LABORATORY',  '临床检验检查', '医技楼 3F', '021-1005', 104 UNION ALL
    SELECT '门诊部',   'OUTPATIENT',  '门诊分诊、导诊管理', '门诊楼 1F', '021-1006', 105 UNION ALL
    SELECT '输液室',   'INFUSION',    '门诊输液执行', '门诊楼 2F', '021-1007', 106
) tmp
WHERE NOT EXISTS (SELECT 1 FROM `department` d WHERE d.dept_code = tmp.dept_code);