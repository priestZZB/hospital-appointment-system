-- =====================================================================
-- Oracle 容器首启初始化（迭代17 M3）：建 8 个业务 schema 与密码
-- 挂载路径：/opt/oracle/scripts/startup（镜像首启自动执行 .sql/.sh）
-- =====================================================================

-- 以 system 执行；FREEPDB1 为 Oracle Free 默认 PDB
ALTER SESSION SET CONTAINER = FREEPDB1;

-- 幂等建用户（ORA-01920 忽略）
WHENEVER SQLERROR CONTINUE;

CREATE USER hospital_auth IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_patient IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_clinic IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_payment IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_medsupply IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_inpatient IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_ai IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;
CREATE USER hospital_admin IDENTIFIED BY "Hospital_2026" QUOTA UNLIMITED ON USERS;

-- 已存在用户补授权
GRANT CONNECT, RESOURCE, CREATE VIEW TO hospital_auth, hospital_patient, hospital_clinic,
  hospital_payment, hospital_medsupply, hospital_inpatient, hospital_ai, hospital_admin;

GRANT UNLIMITED TABLESPACE TO hospital_auth, hospital_patient, hospital_clinic,
  hospital_payment, hospital_medsupply, hospital_inpatient, hospital_ai, hospital_admin;

EXIT;
