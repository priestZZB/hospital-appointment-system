# 基于微服务架构的医院门诊预约挂号系统

毕业设计项目（Java 21 + Spring Boot 3.5 微服务 + Oracle 19c + Vue 3 管理端 + UniApp 患者端）。

## 系统架构

```
患者端(小程序/H5)  管理端(Vue3 + win-design-next)
        │                    │
        └────► 网关 28080 ◄──┘   (Spring Cloud Gateway，JWT 鉴权/限流)
                 │
   ┌─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┐
 auth      patient   clinic   payment  medsupply inpatient     ai      admin
 28081      28082     28083     28084     28085     28086      28087   后台Job
认证/权限   患者档案   门诊/预约  收费/医保  药房/耗材  住院/床位  AI智能层  运营看板
   └─────────┴─────────┴────┬────┴─────────┴─────────┴─────────┴─────────┘
                       Nacos 注册/配置 + Redis 缓存 + MinIO 文件
                                 │
                          Oracle 19c（每服务独立 schema，Flyway 管理）
```

## 服务清单

| 服务 | 端口 | schema | 职责（对应功能迭代） |
| --- | --- | --- | --- |
| hospital-gateway | 28080 | - | 路由聚合、JWT 校验、限流 |
| hospital-auth | 28081 | hospital_auth | 登录/角色/权限（迭代2/12/13/14/15/16 权限码 3110~3148） |
| hospital-patient | 28082 | hospital_patient | 患者建档/实名/文件上传（迭代1/8） |
| hospital-clinic | 28083 | hospital_clinic | 排班/挂号/叫号/病历/处方/检查改约/公告/急诊分诊/遗传排班/优先级叫号/停诊重调度 |
| hospital-payment | 28084 | hospital_payment | 收费/退费/医保结算/财务报表（迭代4/11） |
| hospital-medsupply | 28085 | hospital_medsupply | 药房/发药/麻精专账/代煎/购药配送/设备（迭代7/13/14） |
| hospital-inpatient | 28086 | hospital_inpatient | 住院/床位/手术中心/排床优化（迭代5/6/15） |
| hospital-ai | 28087 | hospital_ai | 智能分诊/CDSS/多轮问诊/爽约预测/候诊预测/用药推荐/报告摘要（迭代10/15/16） |

## 快速启动（本机开发）

1. 依赖：JDK 21、Oracle 19c（localhost:1521/orcl）、Redis、Nacos 8848、Node 18+、pnpm
2. 初始化 8 个 schema（密码 `Hospital_2026`），详见 [docs/部署手册.md](docs/部署手册.md)
3. 构建并启动后端：
   ```bash
   mvn -q -pl hospital-common,hospital-auth,hospital-patient,hospital-clinic,hospital-payment,hospital-medsupply,hospital-inpatient,hospital-ai,hospital-gateway package -DskipTests
   # 依次 java -jar hospital-<svc>/target/hospital-<svc>-1.0.0.jar（auth 起来后再起其余）
   ```
4. 管理端：
   ```bash
   cd admin-web && pnpm install && pnpm dev
   ```
5. 患者端小程序（HBuilderX 导入 `uniapp-mp/`，或 `npm run dev:h5`，详见 [uniapp-mp/README.md](uniapp-mp/README.md)）
6. Docker 一键编排（可选）：`docker compose up -d --build`，详见 M3 说明

## 自动化回归

```bash
mvn -q -pl hospital-api-test exec:java "-Dexec.mainClass=com.hospital.api.ApiTestRunner"
# 退出码 0 = 全部通过；日志 logs/api-test-full.log（342 步 / 16 组）
```

## 文档索引

- [API接口文档.md](API接口文档.md) / [API功能清单.md](API功能清单.md) —— 接口定义
- [docs/功能全景对齐表.md](docs/功能全景对齐表.md) —— 58 项功能与 17 个迭代映射
- [docs/部署手册.md](docs/部署手册.md) —— 部署/运维（M2）
- [docs/接口清单.md](docs/接口清单.md) —— 全量接口速查（按服务分组）
- [服务器部署方案.md](服务器部署方案.md) —— 生产拓扑
- [docs/工作日志.md](docs/工作日志.md) —— 迭代开发日志
- [uniapp-mp/](uniapp-mp/) —— 患者端小程序（K5）
- [docker-compose.yml](docker-compose.yml) —— 容器编排（M3）
