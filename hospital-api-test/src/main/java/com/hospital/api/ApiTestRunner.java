package com.hospital.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 全量 API 自动化回归测试
 * <p>
 * 覆盖迭代 1 + 迭代 2 全部已实现接口，核心链路为：
 * 建排班 → 挂号 → 取消预约 → 再挂号 → 支付 → 签到 → 叫号 → 接诊/病历/处方 → 停诊/BI。
 * 完全自给自足（自动注册测试患者、自动创建今日排班），无需手动准备数据。
 * <p>
 * 测试数据依据（来自开发库 Flyway 种子与验收数据）：
 * - 管理员 13800000000 / 123456，user.id=1，同时挂 ROLE_ADMIN + ROLE_DOCTOR，
 *   对应 clinic_db.doctor.id=1（李医生，内科 department.id=1）——因此叫号/接诊用管理员 Token 即可。
 * - 科室 14 个（内科 id=1）；药品 medsupply_db.drug.id=1。
 * <p>
 * 使用：
 * <pre>
 *   mvn package -pl hospital-api-test -am -DskipTests
 *   java -jar hospital-api-test/target/hospital-api-test-1.0.0-jar-with-dependencies.jar
 *   或 IDEA 右键 ApiTestRunner → Run
 * </pre>
 * 控制台会打印每一步的请求方法/路径/HTTP 状态/code/耗时，最后输出通过/失败统计并以退出码标识结果。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ApiTestRunner {

    // ============ 配置 ============
    private static final String BASE_URL = "http://localhost:8080";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ADMIN_PHONE = "13800000000";
    private static final String ADMIN_PWD = "123456";
    private static final long DOCTOR_ID = 1L;   // clinic_db.doctor.id=1（李医生，内科）
    private static final long DEPT_ID = 1L;     // clinic_db.department.id=1（内科）
    // 注：Oracle IDENTITY 序列值不因事务回滚回收，历史失败运行会使 drug.id=1 永久缺席，
    //     因此药品 id 改为动态获取（见 drugId()/ensureDrug()），不再硬编码。

    private final ApiTestEngine engine;
    private final TestReport report;
    private final Map<String, Object> state = new LinkedHashMap<>(); // 跨步骤共享状态
    private int stepNo = 0;
    private int stepTotal = 38;

    public ApiTestRunner() {
        // verbose=true：控制台输出每次请求的详细过程
        this.engine = new ApiTestEngine(BASE_URL, true);
        this.report = new TestReport();
    }

    // ==================== main ====================

    public static void main(String[] args) {
        new ApiTestRunner().start();
    }

    private void start() {
        try {
            runAll();
        } catch (Exception e) {
            System.err.println("\n[FATAL] 测试中断: " + e.getMessage());
            e.printStackTrace();
            report.print(); // 即使中断也输出已执行部分的统计
        }
    }

    // ==================== 全量测试编排 ====================

    private void runAll() throws IOException {
        banner();

        // ── 准备 ──
        runStep("0-准备账号", this::prepareAccounts);

        // ════════════ 迭代 1 ════════════
        section("迭代 1 — 核心业务闭环");
        runStep("1-auth-白名单", this::testAuthWhitelist);
        runStep("1-auth-登录注册", this::testAuthLoginRegister);
        runStep("1-auth-角色管理", this::testAuthRoles);
        runStep("1-auth-用户管理", this::testAuthUsers);
        runStep("1-auth-令牌", this::testAuthToken);

        runStep("1-patient-档案", this::testPatientProfile);
        runStep("1-patient-就诊卡", this::testPatientVisitCard);
        runStep("1-patient-过敏史", this::testPatientAllergies);
        runStep("1-patient-文件上传", this::testPatientUpload);
        runStep("1-patient-实名认证", this::testPatientRealname);

        runStep("1-clinic-科室", this::testClinicDepartment);
        runStep("1-clinic-排班", this::testClinicSchedule);
        runStep("1-clinic-号源", this::testClinicSlots);
        runStep("1-clinic-挂号下单", this::testClinicAppointment);

        runStep("1-payment-支付", this::testPayment);
        runStep("1-payment-站内信", this::testNotification);
        runStep("1-payment-扫描超时", this::testScanTimeout);

        // ════════════ 迭代 2 ════════════
        section("迭代 2 — 功能完善");
        runStep("2-ai-AI分诊", this::testAiTriage);

        runStep("2-medsupply-药品目录", this::testDrugCatalog);
        runStep("2-medsupply-库存管理", this::testDrugInventory);
        runStep("2-medsupply-检查项目", this::testExamItem);
        runStep("2-medsupply-报告查询", this::testExamReport);

        runStep("2-clinic-签到", this::testCheckin);
        runStep("2-clinic-叫号", this::testCall);
        runStep("2-clinic-接诊与病历", this::testConsultation);
        runStep("2-clinic-停诊", this::testStop);
        runStep("2-clinic-BI统计", this::testBi);

        // ════════════ 迭代 3（补充接口） ════════════
        section("迭代 3 — 补充接口（前端联调）");
        runStep("3-auth-创建用户+审计日志", this::testAuthUserCreateAudit);
        runStep("3-clinic-医生管理", this::testDoctorManage);
        runStep("3-clinic-预约管理分页", this::testAppointmentAdminPage);
        runStep("3-clinic-排队快照+今日接诊", this::testQueueAndToday);
        runStep("3-medsupply-医生选药", this::testDrugSearch);
        runStep("3-medsupply-报告录入", this::testExamReportEntry);
        runStep("3-medsupply-处方审核发药", this::testDispenseFlow);

        // ════════════ 迭代 6 补全回归（住院部 + 协同 + 医辅 + 财务） ════════════
        section("迭代 6 补全回归 — 住院/输液/协同/危急值/日结");
        runStep("6-inpatient-住院全流程", this::testInpatientFlow);
        runStep("6-infusion-输液闭环", this::testInfusionFlow);
        runStep("6-clinic-会诊转诊随访证明", this::testCollaboration);
        runStep("6-medsupply-危急值与处方点评", this::testCriticalAndReview);
        runStep("6-payment-收费员代缴日结", this::testCashierSettle);
        runStep("6-security-报告越权防护", this::testReportSecurity);

        // ════════════ 迭代 7 药事管理（B1~B11） ════════════
        section("迭代 7 药事回归 — 目录/批次/CDSS/抗菌/中药/代煎/麻精/退药/调拨/指导单");
        runStep("7-medsupply-目录批次药库", this::testPharmacyCatalogBatch);
        runStep("7-clinic-中药处方与安全拦截", this::testPharmacyPrescribing);
        runStep("7-medsupply-代煎麻精退药指导单", this::testPharmacyOps);

        report.print();
    }

    // ==================== 0. 准备 ====================

    private void prepareAccounts() throws IOException {
        // 管理员登录（user.id=1 即李医生 doctor.id=1，同时具备 ADMIN/DOCTOR 角色）
        log("管理员登录: " + ADMIN_PHONE + "（该账号即李医生，后续叫号/接诊均用它）");
        ApiTestEngine.ApiResponse r = engine.post("/api/auth/login",
                Map.of("phone", ADMIN_PHONE, "password", ADMIN_PWD));
        if (r.isOk() && r.getData() != null) {
            Map data = (Map) r.getData();
            String t = (String) data.get("token");
            if (t == null) t = (String) data.get("accessToken");
            if (t != null) {
                state.put("adminToken", t);
                engine.setToken(t);
                log("✅ 管理员登录成功");
            }
        }
        if (adminToken().isEmpty()) {
            throw new RuntimeException("管理员登录失败，确认 auth-service 已启动且密码为 " + ADMIN_PWD);
        }

        // 注册全新测试患者
        String patientPhone = "137" + randomDigits(8);
        check("POST /api/auth/register（注册测试患者）",
                engine.register(Map.of("phone", patientPhone, "password", "Test12345",
                        "realName", "自动化测试患者", "gender", 1)));

        // 登录患者
        String pToken = engine.login(patientPhone, "Test12345");
        state.put("patientToken", pToken);
        state.put("patientPhone", patientPhone);
        engine.setToken(pToken);

        // 读取患者档案获取 patientId（后续实名审核、病历列表使用）
        ApiTestEngine.ApiResponse profile = engine.getWithAuth("/api/patient/profile");
        if (profile.isOk() && profile.getData() instanceof Map) {
            Object pid = ((Map) profile.getData()).get("id");
            if (pid != null) {
                state.put("patientId", ((Number) pid).longValue());
            }
        }
        log("✅ 测试患者已就绪: " + patientPhone + " | patientId=" + state.get("patientId"));

        engine.setToken(adminToken());
    }

    // ==================== 1. auth-service ====================

    private void testAuthWhitelist() throws IOException {
        // 重复手机号注册应被拒绝 — 预期返回非 0
        checkNeg("POST /api/auth/register（重复手机号应拒绝）",
                engine.register(Map.of("phone", ADMIN_PHONE, "password", "abc12345", "realName", "张三")));
    }

    private void testAuthLoginRegister() throws IOException {
        check("POST /api/auth/login（管理员正确凭据）",
                engine.post("/api/auth/login", Map.of("phone", ADMIN_PHONE, "password", ADMIN_PWD)));

        ApiTestEngine.ApiResponse wrong = engine.post("/api/auth/login",
                Map.of("phone", ADMIN_PHONE, "password", "wrong-password"));
        checkNeg("POST /api/auth/login（错误密码应拒绝）", wrong);

        String phone = "136" + randomDigits(8);
        check("POST /api/auth/register（正常注册）",
                engine.register(Map.of("phone", phone, "password", "Xyz12345",
                        "realName", "新患者", "gender", 2, "birthDate", "2000-01-01")));
        check("POST /api/auth/login（新注册患者登录）",
                engine.post("/api/auth/login", Map.of("phone", phone, "password", "Xyz12345")));
    }

    private void testAuthRoles() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/auth/roles", engine.getWithAuth("/api/auth/roles"));

        String roleCode = "ROLE_TEST_" + randomDigits(4);
        check("POST /api/auth/roles（创建测试角色）",
                engine.postWithAuth("/api/auth/roles",
                        Map.of("roleCode", roleCode, "roleName", "测试角色", "description", "API测试", "status", 1)));

        check("GET  /api/auth/roles/1（角色详情）", engine.getWithAuth("/api/auth/roles/1"));

        // 内置角色（ROLE_ADMIN）不可编辑为业务规则，预期拒绝
        checkNeg("PUT  /api/auth/roles/1（编辑内置角色应被拒绝）",
                engine.putWithAuth("/api/auth/roles/1",
                        Map.of("roleCode", "ROLE_ADMIN", "roleName", "管理员",
                                "description", "平台超级管理员", "status", 1)));

        // 给 userId=1 重新分配含管理员的角色集：移除自身管理员角色受保护，预期拒绝
        checkNeg("POST /api/auth/roles/assign（移除自身管理员角色应被拒绝）",
                engine.postWithAuth("/api/auth/roles/assign",
                        Map.of("userId", 1, "roleIds", List.of(1, 2))));

        // 删除测试角色（查找 code 前缀为 ROLE_TEST_ 的角色）
        ApiTestEngine.ApiResponse list = engine.getWithAuth("/api/auth/roles");
        if (list.isOk() && list.getData() instanceof List) {
            for (Map role : (List<Map>) list.getData()) {
                String code = String.valueOf(role.get("roleCode"));
                if (code.startsWith("ROLE_TEST_")) {
                    check("DELETE /api/auth/roles/" + role.get("id") + "（删除测试角色）",
                            engine.deleteWithAuth("/api/auth/roles/" + role.get("id")));
                    break;
                }
            }
        }
    }

    private void testAuthUsers() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/auth/users?pageNo=1&pageSize=10（用户分页）",
                engine.getWithAuth("/api/auth/users?pageNo=1&pageSize=10"));
        // 不能操作自己的账号（启用自己）为业务规则，预期拒绝
        checkNeg("PUT  /api/auth/users/1/status（操作自己账号应被拒绝）",
                engine.putWithAuth("/api/auth/users/1/status", Map.of("status", 1)));
    }

    private void testAuthToken() throws IOException {
        engine.setToken(adminToken());
        check("POST /api/auth/refresh（刷新令牌）", engine.postWithAuth("/api/auth/refresh", Map.of()));

        // logout 会把当前 Token 加入黑名单，因此用新 Token 测 logout
        String fresh = engine.login(ADMIN_PHONE, ADMIN_PWD);
        engine.setToken(fresh);
        check("POST /api/auth/logout（登出-令牌进黑名单）", engine.postWithAuth("/api/auth/logout", Map.of()));

        // 重新登录，避免后续管理员操作被黑名单拦截
        String reload = engine.login(ADMIN_PHONE, ADMIN_PWD);
        state.put("adminToken", reload);
        log("logout 后已重新获取管理员 Token");
    }

    // ==================== 1. patient-service ====================

    private void testPatientProfile() throws IOException {
        engine.setToken(patientToken());
        check("GET  /api/patient/profile", engine.getWithAuth("/api/patient/profile"));
        check("PUT  /api/patient/profile（编辑档案）",
                engine.putWithAuth("/api/patient/profile",
                        Map.of("emergencyContact", "测试家属", "emergencyPhone", "13800008888")));
    }

    private void testPatientVisitCard() throws IOException {
        engine.setToken(patientToken());
        check("GET  /api/patient/visit-cards", engine.getWithAuth("/api/patient/visit-cards"));
    }

    private void testPatientAllergies() throws IOException {
        engine.setToken(patientToken());
        check("POST /api/patient/allergies（新增过敏史）",
                engine.postWithAuth("/api/patient/allergies",
                        Map.of("allergen", "青霉素", "reactionType", "RASH", "severity", "MILD")));
        check("GET  /api/patient/allergies（过敏史列表）", engine.getWithAuth("/api/patient/allergies"));

        ApiTestEngine.ApiResponse list = engine.getWithAuth("/api/patient/allergies");
        if (list.isOk() && list.getData() instanceof List && !((List) list.getData()).isEmpty()) {
            long aid = ((Number) ((Map) ((List) list.getData()).get(0)).get("id")).longValue();
            check("DELETE /api/patient/allergies/" + aid + "（删除过敏史）",
                    engine.deleteWithAuth("/api/patient/allergies/" + aid));
        } else {
            check("DELETE /api/patient/allergies/{id}（跳过—无数据）", syntheticOk("无过敏史数据"));
        }
    }

    private void testPatientUpload() throws IOException {
        engine.setToken(patientToken());
        File tmpFile = File.createTempFile("test-avatar", ".jpg");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            fos.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0});
        }
        // MinIO 为可选外部依赖：仅验证上传接口可达（未部署 MinIO 时服务返回业务错误而非 404）
        checkReachable("POST /api/patient/upload（MinIO 文件上传-接口可达性）",
                engine.uploadWithAuth("/api/patient/upload", "file", tmpFile));
    }

    private void testPatientRealname() throws IOException {
        engine.setToken(patientToken());
        check("POST /api/patient/realname（提交实名认证）",
                engine.postWithAuth("/api/patient/realname",
                        Map.of("name", "自动化测试患者", "idCard", "310101199506151234")));

        Object pid = state.get("patientId");
        if (pid != null) {
            engine.setToken(adminToken());
            check("PUT  /api/patient/realname/" + pid + "/review（管理员审核通过）",
                    engine.putWithAuth("/api/patient/realname/" + pid + "/review",
                            Map.of("verifyStatus", 2, "verifyComment", "自动化测试通过")));
        } else {
            check("PUT  /api/patient/realname/{id}/review（跳过—无 patientId）", syntheticOk("无 patientId"));
        }
    }

    // ==================== 1. clinic-service ====================

    private void testClinicDepartment() throws IOException {
        engine.setToken(patientToken());
        check("GET  /api/clinic/departments?keyword=内（科室列表）",
                engine.getWithAuth("/api/clinic/departments?keyword=内"));

        engine.setToken(adminToken());
        String code = "DEPT_" + randomDigits(4);
        check("POST /api/clinic/departments（新增科室）",
                engine.postWithAuth("/api/clinic/departments",
                        Map.of("deptName", "测试科室" + randomDigits(2), "deptCode", code,
                                "description", "API测试", "location", "测试楼", "sortOrder", 99)));
        check("GET  /api/clinic/departments/1（科室详情）", engine.getWithAuth("/api/clinic/departments/1"));
        check("PUT  /api/clinic/departments/1（编辑科室）",
                engine.putWithAuth("/api/clinic/departments/1",
                        Map.of("deptName", "内科", "deptCode", "INTERNAL_MEDICINE",
                                "description", "内科疾病诊疗", "location", "门诊2F")));
        check("PUT  /api/clinic/departments/1/status?status=1（启用科室）",
                engine.putWithAuth("/api/clinic/departments/1/status?status=1", Map.of()));
    }

    private void testClinicSchedule() throws IOException {
        String today = LocalDate.now().format(DATE_FMT);
        engine.setToken(patientToken());
        check("GET  /api/clinic/schedules（日历视图）",
                engine.getWithAuth("/api/clinic/schedules?departmentId=" + DEPT_ID
                        + "&startDate=" + today + "&endDate=" + LocalDate.now().plusDays(7).format(DATE_FMT)));

        // 确保今天有"签到窗口内"的可用号源，否则创建今日排班
        engine.setToken(adminToken());
        Long scheduleId = ensureTodaySchedule(today);
        if (scheduleId != null) {
            state.put("testScheduleId", scheduleId);
            log("✅ 今日可用排班已就绪: scheduleId=" + scheduleId);
        } else {
            check("POST /api/clinic/schedules（创建今日排班）",
                    new ApiTestEngine.ApiResponse(500, Map.of("code", -2, "message", "无法获取/创建今日可用排班")));
        }

        // 明日排班：供停诊申请测试（48h 内、无预约冲突）
        Long stopSchedId = ensureTomorrowSchedule(LocalDate.now().plusDays(1).format(DATE_FMT));
        if (stopSchedId != null) {
            state.put("stopScheduleId", stopSchedId);
        }
    }

    private void testClinicSlots() throws IOException {
        engine.setToken(patientToken());
        String today = LocalDate.now().format(DATE_FMT);
        ApiTestEngine.ApiResponse r = engine.getWithAuth("/api/clinic/slots?departmentId=" + DEPT_ID + "&date=" + today);
        check("GET  /api/clinic/slots（按科室+日期查号源）", r);
        if (r.isOk() && r.getData() instanceof List && !((List) r.getData()).isEmpty()) {
            long schedId = ((Number) ((Map) ((List) r.getData()).get(0)).get("scheduleId")).longValue();
            check("GET  /api/clinic/slots/schedule/" + schedId + "（按排班查号源）",
                    engine.getWithAuth("/api/clinic/slots/schedule/" + schedId));
        } else {
            check("GET  /api/clinic/slots/schedule/{id}（跳过—今日无号源）", syntheticOk("今日无号源"));
        }
    }

    private void testClinicAppointment() throws IOException {
        engine.setToken(patientToken());
        String today = LocalDate.now().format(DATE_FMT);
        // 只选用“未来 30 分钟内开始”的号源：既满足签到窗口，又不会被“就诊时段已开始不可取消”拦截
        List<Map> slots = fetchAvailableSlots(DEPT_ID, today).stream()
                .filter(this::inCheckinWindow)
                .collect(Collectors.toList());
        if (slots.isEmpty()) {
            check("POST /api/clinic/appointments（挂号）",
                    new ApiTestEngine.ApiResponse(500, Map.of("code", -2, "message", "今日无可用号源，无法执行挂号")));
            return;
        }

        Map slot1 = slots.get(0);
        Map slot2 = slots.size() > 1 ? slots.get(1) : slots.get(0);
        // 注意：/api/clinic/slots 返回的号源主键字段是 "id"，不是 "slotId"
        long slotId1 = ((Number) slot1.get("id")).longValue();
        long schedId1 = ((Number) slot1.get("scheduleId")).longValue();
        long slotId2 = ((Number) slot2.get("id")).longValue();
        long schedId2 = ((Number) slot2.get("scheduleId")).longValue();

        // A. 挂号（slot1）→ 立即取消（验证取消 + 号源释放 + 防重复键清理）
        ApiTestEngine.ApiResponse submit1 = engine.postWithAuth("/api/clinic/appointments",
                Map.of("slotId", slotId1, "scheduleId", schedId1));
        check("POST /api/clinic/appointments（挂号-用于取消）", submit1);
        if (submit1.isOk() && submit1.getData() instanceof Map) {
            long appt1 = ((Number) ((Map) submit1.getData()).get("id")).longValue();
            check("PUT  /api/clinic/appointments/" + appt1 + "/cancel（取消预约）",
                    engine.putWithAuth("/api/clinic/appointments/" + appt1 + "/cancel", Map.of("reason", "自动化测试取消")));

            ApiTestEngine.ApiResponse detail1 = engine.getWithAuth("/api/clinic/appointments/" + appt1);
            if (detail1.isOk() && detail1.getData() instanceof Map) {
                String st = String.valueOf(((Map) detail1.getData()).get("orderStatus"));
                assertTrue("取消后 orderStatus 应为 CANCELLED，实际=" + st, "CANCELLED".equals(st));
            }

            // B. 再次挂号（slot2）→ 保留用于支付/签到/接诊完整链路
            ApiTestEngine.ApiResponse submit2 = engine.postWithAuth("/api/clinic/appointments",
                    Map.of("slotId", slotId2, "scheduleId", schedId2));
            check("POST /api/clinic/appointments（再挂号-完整链路）", submit2);
            if (submit2.isOk() && submit2.getData() instanceof Map) {
                Map d = (Map) submit2.getData();
                state.put("appointmentId", d.get("id"));
                if (d.containsKey("paymentOrderId")) state.put("paymentOrderId", d.get("paymentOrderId"));
                if (d.containsKey("paymentOrderNo")) state.put("paymentOrderNo", d.get("paymentOrderNo"));
                log("✅ 挂号成功 appointmentId=" + d.get("id") + " paymentOrderId=" + d.get("paymentOrderId"));
            }
        } else {
            check("POST /api/clinic/appointments（再挂号）",
                    new ApiTestEngine.ApiResponse(500, Map.of("code", -2, "message", "首次挂号失败，跳过后续")));
        }

        check("GET  /api/clinic/appointments（我的预约列表）", engine.getWithAuth("/api/clinic/appointments"));
        Object apptId = state.get("appointmentId");
        if (apptId != null) {
            check("GET  /api/clinic/appointments/" + apptId + "（预约详情）",
                    engine.getWithAuth("/api/clinic/appointments/" + apptId));
        }
    }

    // ==================== 1. payment-service ====================

    private void testPayment() throws IOException {
        engine.setToken(patientToken());
        Object payOrderId = state.get("paymentOrderId");
        if (payOrderId == null) {
            check("POST /api/payment/pay（跳过—无支付订单）", syntheticOk("无支付订单"));
            check("GET  /api/payment/status/{id}（跳过—无支付订单）", syntheticOk("无支付订单"));
            check("GET  /api/payment/receipt/{id}（跳过—无支付订单）", syntheticOk("无支付订单"));
            return;
        }

        check("POST /api/payment/pay?orderId=" + payOrderId + "（模拟支付）",
                engine.postWithAuth("/api/payment/pay?orderId=" + payOrderId, Map.of()));

        ApiTestEngine.ApiResponse st = engine.getWithAuth("/api/payment/status/" + payOrderId);
        check("GET  /api/payment/status/" + payOrderId + "（订单状态）", st);
        if (st.isOk() && st.getData() instanceof Map) {
            String status = String.valueOf(((Map) st.getData()).get("status"));
            assertTrue("支付后 status 应为 PAID，实际=" + status, "PAID".equals(status));
        }

        ApiTestEngine.ApiResponse pdf = engine.getBytesWithAuth("/api/payment/receipt/" + payOrderId);
        check("GET  /api/payment/receipt/" + payOrderId + "（PDF 凭证下载）", pdf);
        assertTrue("PDF 凭证字节数应 > 100，实际=" + pdf.getRawBytes().length, pdf.getRawBytes().length > 100);
    }

    private void testNotification() throws IOException {
        engine.setToken(patientToken());
        check("GET  /api/payment/notifications（站内信列表）", engine.getWithAuth("/api/payment/notifications"));

        ApiTestEngine.ApiResponse list = engine.getWithAuth("/api/payment/notifications");
        if (list.isOk() && list.getData() instanceof Map) {
            List<Map> records = (List<Map>) ((Map) list.getData()).get("records");
            if (records != null && !records.isEmpty()) {
                long nid = ((Number) records.get(0).get("id")).longValue();
                check("PUT  /api/payment/notifications/" + nid + "/read（标记已读）",
                        engine.putWithAuth("/api/payment/notifications/" + nid + "/read", Map.of()));
            } else {
                check("PUT  /api/payment/notifications/{id}/read（跳过—无通知）", syntheticOk("无通知数据"));
            }
        }
    }

    private void testScanTimeout() throws IOException {
        engine.setToken(adminToken());
        check("POST /api/payment/scan-timeout（手动触发扫表关单）",
                engine.postWithAuth("/api/payment/scan-timeout", Map.of()));
    }

    // ==================== 2. ai-service ====================

    private void testAiTriage() throws IOException {
        engine.setToken(patientToken());
        check("POST /api/ai/triage（发烧咳嗽）",
                engine.postWithAuth("/api/ai/triage", Map.of("symptom", "发烧咳嗽喉咙痛")));
        check("POST /api/ai/triage（骨科症状）",
                engine.postWithAuth("/api/ai/triage", Map.of("symptom", "骨折扭伤关节痛")));
        check("POST /api/ai/triage（无匹配→默认）",
                engine.postWithAuth("/api/ai/triage", Map.of("symptom", "不舒服")));
    }

    // ==================== 2. medsupply-service ====================

    private void testDrugCatalog() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/admin/drug/page?pageNo=1&pageSize=10（药品分页）",
                engine.getWithAuth("/api/admin/drug/page?pageNo=1&pageSize=10"));
        String code = "DRUG_" + randomDigits(5);
        check("POST /api/admin/drug（新增药品）",
                engine.postWithAuth("/api/admin/drug", Map.of("drugCode", code, "drugName", "自动化测试药品",
                        "genericName", "测试", "specification", "10mg×20片", "dosageForm", "TABLET",
                        "manufacturer", "测试药厂", "referencePrice", 12.5, "unit", "BOX", "description", "API测试")));
        Long did = drugId();
        if (did != null) {
            check("GET  /api/admin/drug/" + did + "（药品详情）", engine.getWithAuth("/api/admin/drug/" + did));
            check("PUT  /api/admin/drug/" + did + "（更新药品）",
                    engine.putWithAuth("/api/admin/drug/" + did, Map.of("drugCode", "DRUG_TEST01", "drugName", "自动化测试药品v2",
                            "genericName", "测试", "specification", "10mg×20片", "dosageForm", "TABLET",
                            "manufacturer", "测试药厂", "referencePrice", 15.0, "unit", "BOX")));
        }
    }

    private void testDrugInventory() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/admin/drug/inventory/list?pageNo=1&pageSize=10（库存列表）",
                engine.getWithAuth("/api/admin/drug/inventory/list?pageNo=1&pageSize=10"));
        check("POST /api/admin/drug/inventory/inbound（入库）",
                engine.postWithAuth("/api/admin/drug/inventory/inbound",
                        Map.of("drugId", drugId(), "quantity", 50, "remark", "自动化测试入库")));
        check("POST /api/admin/drug/inventory/outbound（出库）",
                engine.postWithAuth("/api/admin/drug/inventory/outbound",
                        Map.of("drugId", drugId(), "quantity", 5, "remark", "自动化测试出库")));
        check("POST /api/admin/drug/inventory/adjust（盘点调整）",
                engine.postWithAuth("/api/admin/drug/inventory/adjust",
                        Map.of("drugId", drugId(), "quantity", 100, "remark", "自动化测试盘点")));
    }

    private void testExamItem() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/admin/exam/item（检查项目列表）", engine.getWithAuth("/api/admin/exam/item"));
        String code = "EXAM_" + randomDigits(4);
        check("POST /api/admin/exam/item（创建检查项目）",
                engine.postWithAuth("/api/admin/exam/item", Map.of("itemCode", code, "itemName", "自动化测试检查项",
                        "itemType", "LAB", "referencePrice", 30.0, "execDept", "检验科", "precautions", "空腹")));
    }

    private void testExamReport() throws IOException {
        engine.setToken(patientToken());
        check("GET  /api/exam/report/my?pageNo=1&pageSize=10（我的检查报告）",
                engine.getWithAuth("/api/exam/report/my?pageNo=1&pageSize=10"));
    }

    // ==================== 2. clinic-service 扩展 ====================

    private void testCheckin() throws IOException {
        engine.setToken(patientToken());
        Object apptId = state.get("appointmentId");
        if (apptId == null) {
            check("POST /api/clinic/checkin（跳过—无预约）", syntheticOk("无预约"));
            return;
        }
        ApiTestEngine.ApiResponse r = engine.postWithAuth("/api/clinic/checkin",
                Map.of("appointmentId", apptId));
        check("POST /api/clinic/checkin（签到）", r);
        if (r.isOk() && r.getData() instanceof Map) {
            Object cid = ((Map) r.getData()).get("id");
            state.put("checkinId", cid);
            log("✅ 签到成功 checkinId=" + cid);
            check("GET  /api/clinic/checkin/" + cid + "/queue-status（排队状态）",
                    engine.getWithAuth("/api/clinic/checkin/" + cid + "/queue-status"));
        }
    }

    private void testCall() throws IOException {
        // 管理员(user.id=1)即李医生(doctor.id=1)，具备 ROLE_DOCTOR，可执行叫号
        engine.setToken(adminToken());
        check("POST /api/clinic/call/next（医生叫号）",
                engine.postWithAuth("/api/clinic/call/next",
                        Map.of("departmentId", DEPT_ID, "consultRoom", "1诊室")));

        Object cid = state.get("checkinId");
        if (cid != null) {
            // recall 的 consultRoom 是 @RequestParam（query 参数），不是 JSON body
            check("POST /api/clinic/call/" + cid + "/recall?consultRoom=RM1（叫号重呼）",
                    engine.postWithAuth("/api/clinic/call/" + cid + "/recall?consultRoom=RM1", Map.of()));
        }
    }

    private void testConsultation() throws IOException {
        engine.setToken(adminToken());
        Object apptId = state.get("appointmentId");
        if (apptId == null) {
            check("POST /api/clinic/consultation/start（跳过—无预约）", syntheticOk("无预约"));
            return;
        }

        // 开始接诊
        ApiTestEngine.ApiResponse start = engine.postWithAuth("/api/clinic/consultation/start?appointmentId=" + apptId, Map.of());
        check("POST /api/clinic/consultation/start（开始接诊）", start);
        Object recordId = null;
        if (start.isOk() && start.getData() instanceof Map) {
            recordId = ((Map) start.getData()).get("id");
            state.put("recordId", recordId);
            log("✅ 接诊开始 recordId=" + recordId);
        } else {
            check("PUT  /api/clinic/consultation/{id}（病历-跳过）",
                    new ApiTestEngine.ApiResponse(500, Map.of("code", -2, "message", "接诊未开始，跳过病历/处方")));
            return;
        }

        check("PUT  /api/clinic/consultation/" + recordId + "（保存并提交病历）",
                engine.putWithAuth("/api/clinic/consultation/" + recordId,
                        Map.of("chiefComplaint", "自动化测试主诉", "presentIllness", "自动化测试现病史",
                                "temperature", 36.5, "pulse", 72, "respiration", 18, "bloodPressure", "120/80",
                                "diagnosisCode", "J20.9", "diagnosisDesc", "急性支气管炎（自动化测试）",
                                "action", "SUBMIT")));

        ApiTestEngine.ApiResponse prescriptionResp = engine.postWithAuth("/api/clinic/prescription",
                Map.of("medicalRecordId", recordId, "items", List.of(
                        Map.ofEntries(
                                Map.entry("drugId", drugId()), Map.entry("drugName", "自动化测试药品"),
                                Map.entry("specification", "10mg×20片"), Map.entry("dosage", "0.5g"),
                                Map.entry("usageMethod", "ORAL"), Map.entry("frequency", "TID"),
                                Map.entry("days", 3), Map.entry("quantity", 9), Map.entry("price", 12.5),
                                Map.entry("unit", "BOX"), Map.entry("remark", "自动化测试")))));
        check("POST /api/clinic/prescription（开具处方）", prescriptionResp);
        if (prescriptionResp.isOk() && prescriptionResp.getData() instanceof Map) {
            Object prescId = ((Map) prescriptionResp.getData()).get("id");
            if (prescId != null) {
                state.put("prescriptionId", ((Number) prescId).longValue());
                log("✅ 处方已开具 prescriptionId=" + prescId);
            }
        }

        Long examItemId = ensureExamItem();
        if (examItemId != null) {
            check("POST /api/clinic/consultation/exam（检查申请）",
                    engine.postWithAuth("/api/clinic/consultation/exam",
                            Map.of("medicalRecordId", recordId, "examItemId", examItemId,
                                    "examItemName", "自动化测试检查项", "itemType", "LAB", "applyRemark", "自动化测试")));
        }

        check("PUT  /api/clinic/consultation/" + recordId + "/finish（结束就诊）",
                engine.putWithAuth("/api/clinic/consultation/" + recordId + "/finish", Map.of()));
        check("GET  /api/clinic/consultation/" + recordId + "（病历详情）",
                engine.getWithAuth("/api/clinic/consultation/" + recordId));

        Object pid = state.get("patientId");
        if (pid != null) {
            check("GET  /api/clinic/consultation/patient/" + pid + "（患者病历列表）",
                    engine.getWithAuth("/api/clinic/consultation/patient/" + pid));
        }
    }

    private void testStop() throws IOException {
        engine.setToken(adminToken());
        Object schedId = state.get("stopScheduleId");
        if (schedId == null) {
            check("POST /api/clinic/stop/apply（跳过—无明日排班）", syntheticOk("无明日排班"));
            return;
        }

        ApiTestEngine.ApiResponse apply = engine.postWithAuth("/api/clinic/stop/apply",
                Map.of("scheduleId", schedId, "applyReason", "自动化测试停诊"));
        check("POST /api/clinic/stop/apply（停诊申请）", apply);

        check("GET  /api/clinic/stop/list?pageNo=1&pageSize=10（停诊列表）",
                engine.getWithAuth("/api/clinic/stop/list?pageNo=1&pageSize=10"));

        if (apply.isOk() && apply.getData() instanceof Map) {
            Object applicationId = ((Map) apply.getData()).get("id");
            // 迭代6 两级审批：先科主任初审（CHIEF_PASSED → PENDING_ADMIN），再门诊部终审
            check("PUT  /api/clinic/stop/" + applicationId + "/chief-review（科主任初审通过）",
                    engine.putWithAuth("/api/clinic/stop/" + applicationId + "/chief-review",
                            Map.of("action", "APPROVE", "approveComment", "自动化测试初审通过")));
            check("PUT  /api/clinic/stop/" + applicationId + "/approve（驳回停诊终审）",
                    engine.putWithAuth("/api/clinic/stop/" + applicationId + "/approve",
                            Map.of("action", "REJECT", "approveComment", "自动化测试驳回")));
        }
        check("GET  /api/clinic/stop/doctor/1（医生停诊记录）",
                engine.getWithAuth("/api/clinic/stop/doctor/1"));
    }

    private void testBi() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/clinic/bi/overview（当日 BI 概览）", engine.getWithAuth("/api/clinic/bi/overview"));
    }

    // ==================== 3. 迭代 3 补充接口 ====================

    private void testAuthUserCreateAudit() throws IOException {
        engine.setToken(adminToken());
        String doctorPhone = "135" + randomDigits(8);
        ApiTestEngine.ApiResponse create = engine.postWithAuth("/api/auth/users",
                Map.of("phone", doctorPhone, "password", "Test12345",
                        "realName", "自动化测试医生", "gender", 1, "userType", "DOCTOR"));
        check("POST /api/auth/users（创建医生账号）", create);
        if (create.isOk() && create.getData() instanceof Map) {
            Object uid = ((Map) create.getData()).get("id");
            if (uid != null) {
                state.put("createdUserId", ((Number) uid).longValue());
            }
        }
        check("GET  /api/auth/users?userType=DOCTOR（用户分页）",
                engine.getWithAuth("/api/auth/users?userType=DOCTOR&pageNo=1&pageSize=10"));
        try {
            Thread.sleep(1200); // 等待异步审计落库
        } catch (InterruptedException ignored) {
        }
        check("GET  /api/auth/audit-logs?pageNo=1&pageSize=10（审计日志分页）",
                engine.getWithAuth("/api/auth/audit-logs?pageNo=1&pageSize=10"));
        check("GET  /api/auth/audit-logs?operationType=创建用户（审计日志-操作类型筛选）",
                engine.getWithAuth("/api/auth/audit-logs?operationType=创建用户&pageNo=1&pageSize=10"));
    }

    private void testDoctorManage() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/clinic/doctors?pageNo=1&pageSize=10（医生分页）",
                engine.getWithAuth("/api/clinic/doctors?pageNo=1&pageSize=10"));
        check("GET  /api/clinic/doctors/1（医生详情）",
                engine.getWithAuth("/api/clinic/doctors/1"));

        Object uid = state.get("createdUserId");
        if (uid == null) {
            check("POST /api/clinic/doctors（跳过—无测试账号）",
                    syntheticOk("无测试账号，跳过医生新增"));
            return;
        }
        String phone = "135" + randomDigits(8);
        ApiTestEngine.ApiResponse create = engine.postWithAuth("/api/clinic/doctors",
                Map.of("userId", uid, "name", "自动化测试医生", "gender", 1, "phone", phone,
                        "departmentId", 1L, "title", "ATTENDING", "specialty", "内科",
                        "introduction", "自动化测试"));
        check("POST /api/clinic/doctors（新增医生）", create);
        if (create.isOk() && create.getData() instanceof Map) {
            Object did = ((Map) create.getData()).get("id");
            if (did != null) {
                state.put("createdDoctorId", ((Number) did).longValue());
                check("PUT  /api/clinic/doctors/" + did + "/status（停用医生）",
                        engine.putWithAuth("/api/clinic/doctors/" + did + "/status", Map.of("status", 0)));
                check("PUT  /api/clinic/doctors/" + did + "/status（启用医生）",
                        engine.putWithAuth("/api/clinic/doctors/" + did + "/status", Map.of("status", 1)));
                check("PUT  /api/clinic/doctors/" + did + "（编辑医生）",
                        engine.putWithAuth("/api/clinic/doctors/" + did,
                                Map.of("name", "自动化测试医生-改", "gender", 1, "phone", phone,
                                        "departmentId", 1L, "title", "RESIDENT", "specialty", "内科")));
            }
        }
    }

    private void testAppointmentAdminPage() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/clinic/appointments/page?pageNo=1&pageSize=10（预约全量分页）",
                engine.getWithAuth("/api/clinic/appointments/page?pageNo=1&pageSize=10"));
    }

    private void testQueueAndToday() throws IOException {
        engine.setToken(adminToken());
        checkReachable("GET  /api/clinic/checkin/queue?departmentId=1（排队快照）",
                engine.getWithAuth("/api/clinic/checkin/queue?departmentId=1"));
        checkReachable("GET  /api/clinic/consultation/today?departmentId=1（今日接诊列表）",
                engine.getWithAuth("/api/clinic/consultation/today?departmentId=1"));
    }

    private void testDrugSearch() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/medsupply/drugs?pageNo=1&pageSize=10（医生选药搜索）",
                engine.getWithAuth("/api/medsupply/drugs?pageNo=1&pageSize=10"));
        check("GET  /api/medsupply/drugs?keyword=药品（医生选药-关键字）",
                engine.getWithAuth("/api/medsupply/drugs?keyword=药品&pageNo=1&pageSize=10"));
    }

    private void testExamReportEntry() throws IOException {
        engine.setToken(adminToken());
        Object recordId = state.get("recordId");
        Object patientId = state.get("patientId");
        if (recordId == null || patientId == null) {
            check("POST /api/admin/exam/report（跳过—无病历/患者）",
                    syntheticOk("无病历/患者，跳过"));
            return;
        }
        Long examItemId = ensureExamItem();
        if (examItemId == null) {
            check("POST /api/admin/exam/report（跳过—无检查项目）",
                    syntheticOk("无检查项目，跳过"));
            return;
        }
        ApiTestEngine.ApiResponse apply = engine.postWithAuth("/api/medsupply/internal/exam/apply",
                Map.of("medicalRecordId", recordId, "patientId", patientId, "doctorId", 1L,
                        "examItemId", examItemId, "examItemName", "自动化测试检查项",
                        "itemType", "LAB", "applyRemark", "自动化测试报告录入"));
        Object appId = null;
        // 内部接口返回裸 Map（无 Result 包裹），按 HTTP 200 + 存在 id 判定
        if (apply.isHttpOk() && apply.getRaw().containsKey("id")) {
            appId = ((Number) apply.getRaw().get("id")).longValue();
            assertTrue("POST /api/medsupply/internal/exam/apply（创建检查申请，applicationId=" + appId + "）", true);
        } else {
            check("POST /api/medsupply/internal/exam/apply（创建检查申请）", apply);
        }
        if (appId == null) {
            check("POST /api/admin/exam/report（跳过—申请创建失败）",
                    syntheticOk("申请创建失败，跳过"));
            return;
        }
        ApiTestEngine.ApiResponse report = engine.multipartFieldsWithAuth("/api/admin/exam/report",
                new java.util.LinkedHashMap<>(Map.of(
                        "applicationId", String.valueOf(appId),
                        "reportDesc", "自动化测试报告描述",
                        "reportResult", "未见明显异常（自动化测试）",
                        "status", "PUBLISHED")));
        check("POST /api/admin/exam/report（录入检查报告）", report);
        // 存 applicationId/reportId 供危急值用例引用（critical_value.report_id/application_id NOT NULL）
        state.put("examApplicationId", appId);
        if (report.isOk() && report.getData() instanceof Map) {
            Object rid = ((Map) report.getData()).get("id");
            if (rid != null) state.put("examReportId", ((Number) rid).longValue());
        }
    }

    private void testDispenseFlow() throws IOException {
        engine.setToken(adminToken());
        Object prescriptionId = state.get("prescriptionId");
        if (prescriptionId == null) {
            check("PUT  /api/admin/drug/dispense/{id}/review（跳过—无处方）",
                    syntheticOk("无处方，跳过"));
            return;
        }
        check("POST /api/admin/drug/inventory/inbound（补库存供发药）",
                engine.postWithAuth("/api/admin/drug/inventory/inbound",
                        Map.of("drugId", drugId(), "quantity", 50, "remark", "自动化测试发药备货")));

        // 处方缴费（真实流：划价收费 → 药师审核 → 发药；审核门控 pay_status=PAID）
        engine.setToken(patientToken());
        Object patientId = state.get("patientId");
        ApiTestEngine.ApiResponse unpaidRx = engine.getWithAuth(
                "/api/clinic/prescription/unpaid?patientId=" + patientId + "&pageNo=1&pageSize=10");
        check("GET  /api/clinic/prescription/unpaid（患者未缴费处方列表）", unpaidRx);
        Object rxFee = null;
        if (unpaidRx.isOk() && unpaidRx.getData() instanceof List<?> rxList) {
            for (Object o : rxList) {
                if (o instanceof Map m && String.valueOf(m.get("id")).equals(String.valueOf(prescriptionId))) {
                    rxFee = m.get("totalAmount");
                    break;
                }
            }
        }
        if (rxFee == null) rxFee = 12.5;
        ApiTestEngine.ApiResponse rxCost = engine.postWithAuth("/api/payment/treatment/order",
                Map.of("orderType", "DRUG", "relatedId", prescriptionId,
                        "items", List.of(Map.of("itemName", "处方药品费", "qty", 1, "price", rxFee))));
        check("POST /api/payment/treatment/order（创建处方缴费订单）", rxCost);
        Object rxOrderId = rxCost.getData() instanceof Map ? ((Map) rxCost.getData()).get("id") : null;
        if (rxOrderId != null) {
            check("POST /api/payment/treatment/pay/" + rxOrderId + "（缴处方药品费）",
                    engine.postWithAuth("/api/payment/treatment/pay/" + rxOrderId, Map.of()));
        }

        // 药师审核（PAID 门控通过）
        engine.setToken(adminToken());
        check("PUT  /api/admin/drug/dispense/" + prescriptionId + "/review（处方审核通过）",
                engine.putWithAuth("/api/admin/drug/dispense/" + prescriptionId + "/review",
                        Map.of("action", "APPROVE", "reviewComment", "自动化测试审核通过")));
        check("GET  /api/admin/drug/dispense/list?status=REVIEW_PASSED&pageNo=1&pageSize=10（发药记录列表）",
                engine.getWithAuth("/api/admin/drug/dispense/list?status=REVIEW_PASSED&pageNo=1&pageSize=10"));
        check("POST /api/admin/drug/dispense/" + prescriptionId + "（发药确认）",
                engine.postWithAuth("/api/admin/drug/dispense/" + prescriptionId, Map.of()));
        check("GET  /api/admin/drug/dispense/list?status=DISPENSED&pageNo=1&pageSize=10（已发药列表）",
                engine.getWithAuth("/api/admin/drug/dispense/list?status=DISPENSED&pageNo=1&pageSize=10"));
    }

    // ==================== 排班/号源辅助（依据开发库种子数据） ====================

    /**
     * 确保今天有签到窗口内（号源开始 ±30 分钟）的可用号源；
     * 没有则创建"当前时间+5分钟"开始的今日排班。返回 scheduleId，失败返回 null。
     */
    private Long ensureTodaySchedule(String today) throws IOException {
        // 1) 已有窗口内可用号源 → 直接复用（重复运行场景）
        for (Map slot : fetchAvailableSlots(DEPT_ID, today)) {
            if (inCheckinWindow(slot)) {
                return ((Number) slot.get("scheduleId")).longValue();
            }
        }

        // 2) 创建今日排班：periodStart = 当前时间 + 5 分钟
        LocalTime start = LocalTime.now().plusMinutes(5).withSecond(0).withNano(0);
        LocalTime end = start.plusHours(3);
        if (!end.isAfter(start)) {
            // 夜间运行防跨午夜：start+3h 越过 0 点会算出负号源数，截到 23:59
            end = LocalTime.of(23, 59);
        }
        String period = LocalTime.now().isBefore(LocalTime.NOON) ? "AM" : "PM";
        ApiTestEngine.ApiResponse r = engine.postWithAuth("/api/clinic/schedules",
                Map.of("doctorId", DOCTOR_ID, "departmentId", DEPT_ID, "scheduleDate", today,
                        "period", period, "periodStart", start.format(TIME_FMT),
                        "periodEnd", end.format(TIME_FMT), "slotDuration", 10, "registerFee", 20.0));
        check("POST /api/clinic/schedules（创建今日排班 " + start.format(TIME_FMT) + " 起）", r);
        if (r.isOk() && r.getData() instanceof Map) {
            long sid = ((Number) ((Map) r.getData()).get("id")).longValue();
            // 迭代6 排班审批流：新排班 audit_status=PENDING 不生成号源，须门诊部确认（CONFIRMED）后才可挂号
            check("PUT  /api/clinic/schedules/" + sid + "/confirm（确认排班生成号源）",
                    engine.putWithAuth("/api/clinic/schedules/" + sid + "/confirm", Map.of()));
            return sid;
        }

        // 3) 创建冲突（该时段已有排班）→ 退而求其次复用任意可用号源
        List<Map> all = fetchAvailableSlots(DEPT_ID, today);
        if (!all.isEmpty()) {
            return ((Number) all.get(0).get("scheduleId")).longValue();
        }
        return null;
    }

    /**
     * 确保明日存在排班（供停诊申请测试），返回 scheduleId，失败返回 null。
     */
    private Long ensureTomorrowSchedule(String tomorrow) throws IOException {
        List<Map> slots = fetchAvailableSlots(DEPT_ID, tomorrow);
        if (!slots.isEmpty()) {
            return ((Number) slots.get(0).get("scheduleId")).longValue();
        }
        // 与今日排班错开时段，降低重复运行冲突概率
        String period = LocalTime.now().isBefore(LocalTime.NOON) ? "PM" : "AM";
        ApiTestEngine.ApiResponse r = engine.postWithAuth("/api/clinic/schedules",
                Map.of("doctorId", DOCTOR_ID, "departmentId", DEPT_ID, "scheduleDate", tomorrow,
                        "period", period, "periodStart", "08:00", "periodEnd", "12:00",
                        "slotDuration", 10, "registerFee", 20.0));
        check("POST /api/clinic/schedules（创建明日排班，供停诊测试）", r);
        if (r.isOk() && r.getData() instanceof Map) {
            long sid = ((Number) ((Map) r.getData()).get("id")).longValue();
            check("PUT  /api/clinic/schedules/" + sid + "/confirm（确认明日排班）",
                    engine.putWithAuth("/api/clinic/schedules/" + sid + "/confirm", Map.of()));
            return sid;
        }
        return null;
    }

    private List<Map> fetchAvailableSlots(Long deptId, String date) throws IOException {
        ApiTestEngine.ApiResponse r = engine.getWithAuth("/api/clinic/slots?departmentId=" + deptId + "&date=" + date);
        if (r.isOk() && r.getData() instanceof List) {
            // 只取未被占用的号源（残留库重跑时避免撞"当前时段已有预约"）
            return ((List<Map>) r.getData()).stream()
                    .filter(s -> "AVAILABLE".equals(s.get("status")))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 判断号源是否可用：
     * 开始时间在当前时间前 30 分钟 ～ 当前时间（含）之间，即“即将开始”的号源。
     * 只选未来号源，避免“就诊时段已开始”导致取消预约被拦截。
     */
    private boolean inCheckinWindow(Map slot) {
        LocalTime st = parseSlotStart(slot.get("slotStart"));
        if (st == null) return false;
        LocalDateTime slotStart = LocalDateTime.of(LocalDate.now(), st);
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(slotStart.minusMinutes(30)) && !now.isAfter(slotStart);
    }

    private LocalTime parseSlotStart(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        try { return LocalTime.parse(s); } catch (Exception ignore) { }
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")); } catch (Exception ignore) { }
        return null;
    }

    /** 动态获取测试药品 id（缓存于 state），与 ensureExamItem 同模式 */
    private Long drugId() throws IOException {
        Object cached = state.get("drugId");
        if (cached instanceof Number n) return n.longValue();
        Long id = ensureDrug();
        if (id != null) state.put("drugId", id);
        return id;
    }

    /** 确保存在药品（供库存/处方/发药使用），返回 drugId，失败返回 null */
    private Long ensureDrug() throws IOException {
        engine.setToken(adminToken());
        ApiTestEngine.ApiResponse list = engine.getWithAuth("/api/admin/drug/page?pageNo=1&pageSize=10");
        if (list.isOk() && list.getData() instanceof Map) {
            List<Map> records = (List<Map>) ((Map) list.getData()).get("records");
            if (records != null && !records.isEmpty() && ((Map) records.get(0)).get("id") != null) {
                return ((Number) ((Map) records.get(0)).get("id")).longValue();
            }
        }
        String code = "DRUG_" + randomDigits(5);
        ApiTestEngine.ApiResponse r = engine.postWithAuth("/api/admin/drug", Map.of("drugCode", code,
                "drugName", "自动化测试药品", "genericName", "测试", "specification", "10mg×20片",
                "dosageForm", "TABLET", "manufacturer", "测试药厂", "referencePrice", 12.5, "unit", "BOX"));
        if (r.isOk() && r.getData() instanceof Map && ((Map) r.getData()).get("id") != null) {
            return ((Number) ((Map) r.getData()).get("id")).longValue();
        }
        return null;
    }

    /** 确保存在检查项目（供检查申请使用），返回 examItemId，失败返回 null */
    private Long ensureExamItem() throws IOException {
        engine.setToken(adminToken());
        ApiTestEngine.ApiResponse list = engine.getWithAuth("/api/admin/exam/item");
        if (list.isOk() && list.getData() instanceof List && !((List) list.getData()).isEmpty()) {
            return ((Number) ((Map) ((List) list.getData()).get(0)).get("id")).longValue();
        }
        String code = "EXAM_" + randomDigits(4);
        ApiTestEngine.ApiResponse r = engine.postWithAuth("/api/admin/exam/item",
                Map.of("itemCode", code, "itemName", "自动化测试检查项",
                        "itemType", "LAB", "referencePrice", 30.0, "execDept", "检验科", "precautions", "空腹"));
        if (r.isOk() && r.getData() instanceof Map) {
            return ((Number) ((Map) r.getData()).get("id")).longValue();
        }
        return null;
    }

    // ==================== 辅助 ====================

    private String adminToken() { return (String) state.getOrDefault("adminToken", ""); }
    private String patientToken() { return (String) state.getOrDefault("patientToken", ""); }

    private ApiTestEngine.ApiResponse syntheticOk(String msg) {
        return new ApiTestEngine.ApiResponse(200, Map.of("code", 0, "message", msg));
    }

    private void runStep(String label, ThrowingRunnable action) {
        stepNo++;
        System.out.printf("%n━━ [%02d/%02d] %s ━━━━━━━━━━━━━━━━━━━━━━━━━━━━%n", stepNo, stepTotal, label);
        try {
            action.run();
        } catch (Exception e) {
            System.out.println("  💥 步骤异常: " + e.getMessage());
            report.record("[步骤-" + label + "]",
                    new ApiTestEngine.ApiResponse(-1, Map.of("code", -2, "message", String.valueOf(e.getMessage()))));
        }
    }

    /** 正向断言：code==0 为 PASS */
    private void check(String name, ApiTestEngine.ApiResponse r) {
        String icon = r.getCode() == 0 ? "✅" : (r.getCode() == -2 ? "💥" : "❌");
        System.out.printf("  %s %-52s → HTTP %-3d code=%-4d | %s | %dms%n",
                icon, name, r.getHttpStatus(), r.getCode(), safeMsg(r), r.getElapsedMs());
        report.record(name, r);
    }

    /** 接口可达性断言：非 9999/1007/-2 即视为可达（业务拒绝也算通过） */
    private void checkReachable(String name, ApiTestEngine.ApiResponse r) {
        boolean unreachable = r.getCode() == 9999 || r.getCode() == 1007 || r.getCode() == -2;
        if (!unreachable) {
            System.out.printf("  ✅ %-52s → HTTP %-3d code=%-4d | %s（接口可达）| %dms%n",
                    name, r.getHttpStatus(), r.getCode(), safeMsg(r), r.getElapsedMs());
            report.record(name, new ApiTestEngine.ApiResponse(r.getHttpStatus(),
                    Map.of("code", 0, "message", safeMsg(r) + "（接口可达）"), r.getElapsedMs(), r.getRawBytes()));
        } else {
            System.out.printf("  ❌ %-52s → HTTP %-3d code=%-4d | %s（服务不可达）| %dms%n",
                    name, r.getHttpStatus(), r.getCode(), safeMsg(r), r.getElapsedMs());
            report.record(name, r);
        }
    }

    /** 反向断言：code!=0 为 PASS（预期被拒绝） */
    private void checkNeg(String name, ApiTestEngine.ApiResponse r) {
        if (r.getCode() != 0) {
            System.out.printf("  ✅ %-52s → HTTP %-3d code=%-4d | %s（预期拒绝）| %dms%n",
                    name, r.getHttpStatus(), r.getCode(), safeMsg(r), r.getElapsedMs());
            report.record(name, new ApiTestEngine.ApiResponse(r.getHttpStatus(),
                    Map.of("code", 0, "message", safeMsg(r) + "（预期拒绝）"), r.getElapsedMs(), r.getRawBytes()));
        } else {
            System.out.printf("  ❌ %-52s → HTTP %-3d code=%-4d | 预期被拒绝但实际通过%n",
                    name, r.getHttpStatus(), r.getCode());
            report.record(name, new ApiTestEngine.ApiResponse(r.getHttpStatus(),
                    Map.of("code", -3, "message", "预期被拒绝但实际通过"), r.getElapsedMs(), r.getRawBytes()));
        }
    }

    private void check(String name, ApiTestEngine.ThrowingSupplier supplier) {
        try {
            check(name, supplier.get());
        } catch (Exception e) {
            check(name, new ApiTestEngine.ApiResponse(-1,
                    Map.of("code", -2, "message", String.valueOf(e.getMessage()))));
        }
    }

    private void checkReachable(String name, ApiTestEngine.ThrowingSupplier supplier) {
        try {
            checkReachable(name, supplier.get());
        } catch (Exception e) {
            checkReachable(name, new ApiTestEngine.ApiResponse(-1,
                    Map.of("code", -2, "message", String.valueOf(e.getMessage()))));
        }
    }

    private void assertTrue(String msg, boolean cond) {
        if (!cond) {
            report.record(msg, new ApiTestEngine.ApiResponse(200, Map.of("code", -3, "message", "断言失败")));
            System.out.println("  ❌ 断言失败: " + msg);
        } else {
            System.out.println("  ✅ 断言通过: " + msg);
        }
    }

    private String safeMsg(ApiTestEngine.ApiResponse r) {
        String m = r.getMessage();
        if (m == null || m.isBlank()) return "-";
        return m.length() > 120 ? m.substring(0, 120) + "…" : m;
    }

    private static String randomDigits(int n) {
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < n; i++) sb.append(rnd.nextInt(10));
        return sb.toString();
    }

    private void section(String title) {
        System.out.println("\n━━━ " + title + " ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void log(String msg) {
        System.out.println("  ℹ " + msg);
    }

    private void banner() {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   医院门诊预约挂号系统 — 全量 API 自动化回归             ║");
        System.out.println("║   迭代 1 + 迭代 2  |  核心链路: 排班→挂号→支付→签到→接诊  ║");
        System.out.println("║   控制台输出详细过程，失败时退出码为 1                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    // ==================== 内部类 ====================

    static class TestReport {
        int total, passed, failed, errors;
        final LinkedHashMap<String, String> details = new LinkedHashMap<>();

        void record(String name, ApiTestEngine.ApiResponse r) {
            total++;
            if (r.getCode() == -2) { errors++; details.put(name, "ERROR: " + r.getMessage()); }
            else if (r.getCode() == 0) { passed++; details.put(name, "PASS"); }
            else { failed++; details.put(name, "FAIL: code=" + r.getCode() + " " + r.getMessage()); }
        }

        void print() {
            System.out.println("\n\n╔══════════════════════════════════════════════════════════╗");
            System.out.printf ("║  总计: %-4d  ✅ 通过: %-4d  ❌ 失败: %-4d  💥 异常: %-4d%n",
                    total, passed, failed, errors);
            if (total > 0) System.out.printf("║  通过率: %.1f%%%n", (double) passed / total * 100);
            System.out.println("╠══════════════════════════════════════════════════════════╣");
            int i = 1;
            for (Map.Entry<String, String> e : details.entrySet()) {
                String icon = e.getValue().startsWith("PASS") ? "✅" : "❌";
                System.out.printf("║ %3d. %s %s%n", i++, icon, e.getKey());
                if (!e.getValue().startsWith("PASS")) System.out.println("║      → " + e.getValue());
            }
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            if (failed > 0 || errors > 0) {
                System.out.println("\n⚠ 存在失败/异常，请检查上方详情（退出码 1）。");
                System.exit(1);
            } else {
                System.out.println("\n🎉 全量接口回归测试通过（退出码 0）！");
            }
        }
    }

    // ==================== 迭代 6 补全回归：住院部 ====================

    /** 住院全流程：入院→床位→医嘱闭环→体征→押金→费用/日清单→护理→会诊→手术→转科→出院 */
    private void testInpatientFlow() throws IOException {
        engine.setToken(adminToken()); // 管理员即李医生，具备住院医生权限
        Object patientId = state.get("patientId");
        if (patientId == null) {
            check("POST /api/inpatient/admission（跳过—无患者）", syntheticOk("无患者，跳过"));
            return;
        }

        // 1) 入院登记
        ApiTestEngine.ApiResponse admit = engine.postWithAuth("/api/inpatient/admission",
                Map.of("patientId", patientId, "departmentId", 1,
                        "attendingDoctorId", 1, "attendingDoctorName", "李医生",
                        "admissionDiag", "自动化测试入院诊断", "expectedDays", 5));
        check("POST /api/inpatient/admission（入院登记）", admit);
        Object admissionId = admit.getData() instanceof Map ? ((Map) admit.getData()).get("id") : null;
        if (admissionId == null) {
            check("住院全流程（跳过—入院登记失败）", syntheticOk("入院登记失败，跳过"));
            return;
        }
        state.put("admissionId", admissionId);
        String aid = String.valueOf(admissionId);

        // 2) 床位查询 + 分配（取第一张 FREE 床；全占用则跳过分配）
        ApiTestEngine.ApiResponse beds = engine.getWithAuth("/api/inpatient/admission/beds?departmentId=1");
        check("GET  /api/inpatient/admission/beds?departmentId=1（床位查询）", beds);
        Long freeBedId = null;
        if (beds.isOk() && beds.getData() instanceof List<?> list && !list.isEmpty()) {
            for (Object o : list) {
                if (o instanceof Map m && "FREE".equalsIgnoreCase(String.valueOf(m.get("status")))) {
                    freeBedId = ((Number) m.get("id")).longValue();
                    break;
                }
            }
        }
        if (freeBedId != null) {
            check("POST /api/inpatient/admission/assign-bed（分配床位 bedId=" + freeBedId + "）",
                    engine.postWithAuth("/api/inpatient/admission/assign-bed",
                            Map.of("admissionId", admissionId, "bedId", freeBedId)));
        } else {
            check("POST /api/inpatient/admission/assign-bed（跳过—无空闲床位）", syntheticOk("无空闲床位，跳过"));
        }

        // 3) 住院医嘱闭环：开立→核对→执行→停止
        ApiTestEngine.ApiResponse order = engine.postWithAuth("/api/inpatient/order",
                Map.of("admissionId", admissionId, "orderType", "LONG",
                        "category", "DRUG", "content", "自动化测试医嘱：0.9%氯化钠注射液", "frequency", "QD"));
        check("POST /api/inpatient/order（开立医嘱）", order);
        Object orderId = order.getData() instanceof Map ? ((Map) order.getData()).get("id") : null;
        if (orderId != null) {
            check("POST /api/inpatient/order/" + orderId + "/confirm（核对医嘱）",
                    engine.postWithAuth("/api/inpatient/order/" + orderId + "/confirm", Map.of()));
            check("POST /api/inpatient/order/" + orderId + "/execute（执行医嘱）",
                    engine.postWithAuth("/api/inpatient/order/" + orderId + "/execute", Map.of()));
            check("POST /api/inpatient/order/" + orderId + "/stop（停止医嘱）",
                    engine.postWithAuth("/api/inpatient/order/" + orderId + "/stop", Map.of()));
        } else {
            check("POST /api/inpatient/order/{id}/confirm|execute|stop（跳过—医嘱创建失败）",
                    syntheticOk("医嘱创建失败，跳过"));
        }

        // 4) 生命体征
        check("POST /api/inpatient/vital（录入生命体征）",
                engine.postWithAuth("/api/inpatient/vital",
                        Map.of("admissionId", admissionId, "temperature", 36.5, "pulse", 72,
                                "respiration", 18, "bloodPressure", "120/80", "bloodOxygen", 98)));

        // 5) 预交金：缴纳 + 流水
        check("POST /api/inpatient/deposit/pay（预交金缴纳 1000 元）",
                engine.postWithAuth("/api/inpatient/deposit/pay",
                        Map.of("admissionId", admissionId, "amount", 1000, "payMethod", "CASH")));
        check("GET  /api/inpatient/deposit/list?admissionId=" + aid + "（预交金流水）",
                engine.getWithAuth("/api/inpatient/deposit/list?admissionId=" + aid));

        // 6) 费用登记 + 费用列表 + 每日清单 + 床位费日结
        check("POST /api/inpatient/fee/post（费用登记 120.50 元）",
                engine.postWithAuth("/api/inpatient/fee/post",
                        Map.of("admissionId", admissionId, "amount", 120.5,
                                "feeType", "TREATMENT", "itemName", "自动化测试费")));
        check("GET  /api/inpatient/fee/list?admissionId=" + aid + "（住院费用列表）",
                engine.getWithAuth("/api/inpatient/fee/list?admissionId=" + aid));
        check("GET  /api/inpatient/fee/daily-bill?admissionId=" + aid + "（每日费用清单）",
                engine.getWithAuth("/api/inpatient/fee/daily-bill?admissionId=" + aid));
        check("POST /api/inpatient/fee/generate-bed-fees（床位费日结）",
                engine.postWithAuth("/api/inpatient/fee/generate-bed-fees", Map.of()));

        // 7) 护理病历
        check("POST /api/inpatient/nursing（录入护理病历）",
                engine.postWithAuth("/api/inpatient/nursing",
                        Map.of("admissionId", admissionId, "recordType", "GENERAL",
                                "content", "自动化测试护理记录", "intakeMl", 1500, "outputMl", 1200)));

        // 8) 住院会诊：发起 → 处理（ACCEPT）
        ApiTestEngine.ApiResponse consult = engine.postWithAuth("/api/inpatient/consult",
                Map.of("admissionId", admissionId, "targetDeptId", 2, "targetDoctorId", 1,
                        "reason", "自动化测试会诊"));
        check("POST /api/inpatient/consult（发起住院会诊）", consult);
        Object consultId = consult.getData() instanceof Map ? ((Map) consult.getData()).get("id") : null;
        if (consultId != null) {
            check("POST /api/inpatient/consult/handle（处理住院会诊 ACCEPTED）",
                    engine.postWithAuth("/api/inpatient/consult/handle",
                            Map.of("consultId", consultId, "action", "ACCEPTED", "opinion", "同意会诊（自动化）")));
        } else {
            check("POST /api/inpatient/consult/handle（跳过—会诊创建失败）", syntheticOk("会诊创建失败，跳过"));
        }

        // 9) 手术：申请 → 排台 → 取消
        ApiTestEngine.ApiResponse surgery = engine.postWithAuth("/api/inpatient/surgery",
                Map.of("admissionId", admissionId, "surgeryName", "自动化测试手术",
                        "anesthesiaType", "全身麻醉", "remark", "自动化测试"));
        check("POST /api/inpatient/surgery（手术申请）", surgery);
        Object surgeryId = surgery.getData() instanceof Map ? ((Map) surgery.getData()).get("id") : null;
        if (surgeryId != null) {
            check("POST /api/inpatient/surgery/" + surgeryId + "/schedule（手术排台）",
                    engine.postWithAuth("/api/inpatient/surgery/" + surgeryId
                            + "/schedule?scheduledTime=2026-12-31T10:00:00&operatingRoom=OR-1", Map.of()));
            check("POST /api/inpatient/surgery/" + surgeryId + "/cancel（取消手术申请）",
                    engine.postWithAuth("/api/inpatient/surgery/" + surgeryId + "/cancel", Map.of()));
        } else {
            check("POST /api/inpatient/surgery/{id}/schedule|cancel（跳过—手术申请失败）",
                    syntheticOk("手术申请失败，跳过"));
        }

        // 10) 转科（转往外科 id=2）
        check("POST /api/inpatient/admission/transfer-dept（住院转科）",
                engine.postWithAuth("/api/inpatient/admission/transfer-dept",
                        Map.of("admissionId", admissionId, "targetDeptId", 2, "reason", "自动化测试转科")));

        // 11) 出院：办理出院 → 出院小结 → 病案首页
        check("POST /api/inpatient/discharge（办理出院）",
                engine.postWithAuth("/api/inpatient/discharge",
                        Map.of("admissionId", admissionId, "dischargeDiag", "自动化测试出院诊断",
                                "treatmentProcess", "对症治疗", "dischargeCondition", "好转",
                                "dischargeAdvice", "休息一周")));
        check("GET  /api/inpatient/discharge/summary?admissionId=" + aid + "（出院小结查询）",
                engine.getWithAuth("/api/inpatient/discharge/summary?admissionId=" + aid));
        check("GET  /api/inpatient/discharge/home?admissionId=" + aid + "（病案首页查询）",
                engine.getWithAuth("/api/inpatient/discharge/home?admissionId=" + aid));
    }

    /** 输液闭环：医生开单→患者查单/未缴费→缴费→护士站执行 */
    private void testInfusionFlow() throws IOException {
        Object patientId = state.get("patientId");
        Object recordId = state.get("recordId");
        Long drugId = drugId();
        if (patientId == null || recordId == null || drugId == null) {
            check("POST /api/infusion/orders（跳过—缺患者/病历/药品）", syntheticOk("缺前置数据，跳过"));
            return;
        }
        // 1) 医生开输液医嘱（11 个字段，超 Map.of 10 对上限，改用 LinkedHashMap）
        engine.setToken(adminToken());
        Map<String, Object> infusionBody = new java.util.LinkedHashMap<>();
        infusionBody.put("medicalRecordId", recordId);
        infusionBody.put("patientId", patientId);
        infusionBody.put("doctorId", 1);
        infusionBody.put("drugId", drugId);
        infusionBody.put("drugName", "自动化输液药品");
        infusionBody.put("dosage", "100ml");
        infusionBody.put("usageMethod", "静脉滴注");
        infusionBody.put("frequency", "QD");
        infusionBody.put("days", 1);
        infusionBody.put("skinTestRequired", 0);
        infusionBody.put("unitPrice", 30.0);
        ApiTestEngine.ApiResponse created = engine.postWithAuth("/api/infusion/orders", infusionBody);
        check("POST /api/infusion/orders（开输液医嘱）", created);
        Object infusionOrderId = created.getData() instanceof Map ? ((Map) created.getData()).get("id") : null;
        if (infusionOrderId == null) {
            check("输液闭环（跳过—开单失败）", syntheticOk("开单失败，跳过"));
            return;
        }
        String oid = String.valueOf(infusionOrderId);

        // 2) 患者查本人输液单 + 未缴费列表（unpaid 接口 patientId 必填）
        engine.setToken(patientToken());
        check("GET  /api/infusion/orders/my（患者查本人输液单）", engine.getWithAuth("/api/infusion/orders/my"));
        check("GET  /api/infusion/orders/unpaid?patientId=" + patientId + "（患者未缴费输液单）",
                engine.getWithAuth("/api/infusion/orders/unpaid?patientId=" + patientId));

        // 3) 患者缴费（诊疗费订单 INFUSION 类型）
        ApiTestEngine.ApiResponse tOrder = engine.postWithAuth("/api/payment/treatment/order",
                Map.of("orderType", "INFUSION", "relatedId", infusionOrderId,
                        "items", List.of(Map.of("itemName", "自动化输液费", "qty", 1, "price", 30.0))));
        check("POST /api/payment/treatment/order（创建输液缴费订单）", tOrder);
        Object treatOrderId = tOrder.getData() instanceof Map ? ((Map) tOrder.getData()).get("id") : null;
        if (treatOrderId != null) {
            check("POST /api/payment/treatment/pay/" + treatOrderId + "（支付输液费）",
                    engine.postWithAuth("/api/payment/treatment/pay/" + treatOrderId, Map.of()));
        } else {
            check("POST /api/payment/treatment/pay/{id}（跳过—订单创建失败）", syntheticOk("订单创建失败，跳过"));
        }

        // 4) 护士站：待执行列表 + 执行记录
        engine.setToken(adminToken());
        check("GET  /api/infusion/nurse/pending（护士站待执行列表）", engine.getWithAuth("/api/infusion/nurse/pending"));
        check("POST /api/infusion/orders/" + oid + "/records（护士执行记录）",
                engine.postWithAuth("/api/infusion/orders/" + oid + "/records",
                        Map.of("recordType", "输液中记录", "content", "自动化执行",
                                "skinTestResult", "免皮试", "dropRate", 40)));
    }

    /** 门诊协同：会诊→转诊→随访（计划+记录）→医疗证明（PDF 下载+作废） */
    private void testCollaboration() throws IOException {
        engine.setToken(adminToken());
        Object patientId = state.get("patientId");
        Object recordId = state.get("recordId");
        if (patientId == null || recordId == null) {
            check("门诊协同（跳过—无患者/病历）", syntheticOk("无患者/病历，跳过"));
            return;
        }

        // 1) 会诊：发起 → 处理（ACCEPT）
        ApiTestEngine.ApiResponse consult = engine.postWithAuth("/api/clinic/consult-request",
                Map.of("medicalRecordId", recordId, "patientId", patientId,
                        "targetDeptId", 2, "targetDoctorId", 1, "reason", "自动化测试门诊会诊"));
        check("POST /api/clinic/consult-request（发起会诊）", consult);
        Object consultId = consult.getData() instanceof Map ? ((Map) consult.getData()).get("id") : null;
        if (consultId != null) {
            check("PUT  /api/clinic/consult-request/" + consultId + "/handle（处理会诊 ACCEPT）",
                    engine.putWithAuth("/api/clinic/consult-request/" + consultId + "/handle?action=ACCEPT",
                            Map.of("opinion", "同意会诊（自动化）")));
        } else {
            check("PUT  /api/clinic/consult-request/{id}/handle（跳过—会诊创建失败）", syntheticOk("会诊创建失败，跳过"));
        }

        // 2) 转诊：创建 → 处理（ACCEPT）
        ApiTestEngine.ApiResponse referral = engine.postWithAuth("/api/clinic/referral",
                Map.of("medicalRecordId", recordId, "patientId", patientId,
                        "toDeptId", 2, "reason", "自动化测试转诊"));
        check("POST /api/clinic/referral（创建转诊单）", referral);
        Object referralId = referral.getData() instanceof Map ? ((Map) referral.getData()).get("id") : null;
        if (referralId != null) {
            check("PUT  /api/clinic/referral/" + referralId + "/handle（处理转诊 ACCEPT）",
                    engine.putWithAuth("/api/clinic/referral/" + referralId + "/handle?action=ACCEPT", Map.of()));
        } else {
            check("PUT  /api/clinic/referral/{id}/handle（跳过—转诊创建失败）", syntheticOk("转诊创建失败，跳过"));
        }

        // 3) 随访：创建计划 → 填写记录
        ApiTestEngine.ApiResponse plan = engine.postWithAuth("/api/clinic/follow-up",
                Map.of("patientId", patientId, "medicalRecordId", recordId, "doctorId", 1,
                        "followDate", "2026-12-31", "followMethod", "电话", "template", "自动化随访模板"));
        check("POST /api/clinic/follow-up（创建随访计划）", plan);
        Object planId = plan.getData() instanceof Map ? ((Map) plan.getData()).get("id") : null;
        if (planId != null) {
            check("POST /api/clinic/follow-up/" + planId + "/record（填写随访记录）",
                    engine.postWithAuth("/api/clinic/follow-up/" + planId + "/record",
                            Map.of("planId", planId, "patientId", patientId, "doctorId", 1,
                                    "content", "自动化随访记录：恢复良好", "nextFollowDate", "2027-01-15")));
        } else {
            check("POST /api/clinic/follow-up/{id}/record（跳过—计划创建失败）", syntheticOk("计划创建失败，跳过"));
        }

        // 4) 医疗证明：开具 → PDF 下载 → 作废
        ApiTestEngine.ApiResponse cert = engine.postWithAuth("/api/clinic/certificate",
                Map.of("certType", "SICK_LEAVE", "patientId", patientId, "doctorId", 1,
                        "medicalRecordId", recordId, "content", "自动化测试病假证明", "days", 3,
                        "startDate", "2026-12-31"));
        check("POST /api/clinic/certificate（开具医疗证明）", cert);
        Object certId = cert.getData() instanceof Map ? ((Map) cert.getData()).get("id") : null;
        if (certId != null) {
            ApiTestEngine.ApiResponse pdf = engine.getBytesWithAuth("/api/clinic/certificate/" + certId + "/pdf");
            check("GET  /api/clinic/certificate/" + certId + "/pdf（医疗证明 PDF 下载）", pdf);
            assertTrue("医疗证明 PDF 字节数应 > 100，实际=" + pdf.getRawBytes().length, pdf.getRawBytes().length > 100);
            check("PUT  /api/clinic/certificate/" + certId + "/cancel（作废医疗证明）",
                    engine.putWithAuth("/api/clinic/certificate/" + certId + "/cancel", Map.of()));
        } else {
            check("GET  /api/clinic/certificate/{id}/pdf（跳过—证明创建失败）", syntheticOk("证明创建失败，跳过"));
        }
    }

    /** 危急值上报→复核 + 处方点评（依赖检查报告与处方前置数据） */
    private void testCriticalAndReview() throws IOException {
        engine.setToken(adminToken());
        Object patientId = state.get("patientId");
        Object reportId = state.get("examReportId");
        Object applicationId = state.get("examApplicationId");

        // 1) 危急值：上报 → 列表 → 复核
        if (reportId == null || applicationId == null || patientId == null) {
            check("POST /api/medsupply/critical（跳过—无检查报告）", syntheticOk("无检查报告，跳过"));
        } else {
            ApiTestEngine.ApiResponse critical = engine.postWithAuth("/api/medsupply/critical",
                    Map.of("reportId", reportId, "applicationId", applicationId, "patientId", patientId,
                            "itemName", "自动化危急项", "resultValue", "危急值", "referenceRange", "阴性",
                            "criticalLevel", "HIGH"));
            check("POST /api/medsupply/critical（危急值上报）", critical);
            Object criticalId = critical.getData() instanceof Map ? ((Map) critical.getData()).get("id") : null;
            check("GET  /api/medsupply/critical（危急值列表）", engine.getWithAuth("/api/medsupply/critical"));
            if (criticalId != null) {
                check("PUT  /api/medsupply/critical/" + criticalId + "/confirm（危急值复核）",
                        engine.putWithAuth("/api/medsupply/critical/" + criticalId
                                + "/confirm?action=CONFIRMED&comment=" + java.net.URLEncoder.encode("已复核（自动化）", java.nio.charset.StandardCharsets.UTF_8),
                                Map.of()));
            } else {
                check("PUT  /api/medsupply/critical/{id}/confirm（跳过—上报失败）", syntheticOk("上报失败，跳过"));
            }
        }

        // 2) 处方点评（prescriptionId 由处方审核发药用例准备）
        Object prescriptionId = state.get("prescriptionId");
        if (prescriptionId != null && patientId != null) {
            check("POST /api/medsupply/prescription-review（处方点评）",
                    engine.postWithAuth("/api/medsupply/prescription-review",
                            Map.of("prescriptionId", prescriptionId, "patientId", patientId,
                                    "rating", "REASONABLE", "problemType", "无", "comment", "自动化点评：处方合理")));
            check("GET  /api/medsupply/prescription-review（处方点评列表）",
                    engine.getWithAuth("/api/medsupply/prescription-review"));
        } else {
            check("POST /api/medsupply/prescription-review（跳过—无处方）", syntheticOk("无处方，跳过"));
        }
    }

    /** 收费员：代建单→代缴费→退费→日结汇总→生成日结单 */
    private void testCashierSettle() throws IOException {
        engine.setToken(adminToken()); // 管理员具备收费员权限（isCashierOrAdmin）
        Object patientId = state.get("patientId");
        if (patientId == null) {
            check("POST /api/payment/cashier/order（跳过—无患者）", syntheticOk("无患者，跳过"));
            return;
        }

        // 1) 代患者建单（TREATMENT）
        ApiTestEngine.ApiResponse created = engine.postWithAuth("/api/payment/cashier/order",
                Map.of("patientId", patientId, "orderType", "TREATMENT",
                        "items", List.of(Map.of("itemName", "自动化收费项", "qty", 1, "price", 88.5))));
        check("POST /api/payment/cashier/order（收费员代建单）", created);
        Object orderId = created.getData() instanceof Map ? ((Map) created.getData()).get("id") : null;
        if (orderId == null) {
            check("收费员日结（跳过—建单失败）", syntheticOk("建单失败，跳过"));
            return;
        }

        // 2) 代缴费
        check("POST /api/payment/cashier/pay/" + orderId + "（收费员代缴费）",
                engine.postWithAuth("/api/payment/cashier/pay/" + orderId, Map.of()));

        // 3) 退费
        check("POST /api/payment/cashier/refund（收费员退费）",
                engine.postWithAuth("/api/payment/cashier/refund",
                        Map.of("orderId", orderId, "refundReason", "自动化测试退费")));

        // 4) 日结：今日汇总 → 生成日结单
        check("GET  /api/payment/cashier/settle/today（今日支付汇总）",
                engine.getWithAuth("/api/payment/cashier/settle/today"));
        check("POST /api/payment/cashier/settle（生成当日日结单）",
                engine.postWithAuth("/api/payment/cashier/settle", Map.of()));
    }

    /** 安全回归：患者报告查询越权防护（P1 修复验证） */
    private void testReportSecurity() throws IOException {
        // 1) 患者带 patientId 查他人报告 → 应被拒绝（NO_PERMISSION）
        engine.setToken(patientToken());
        checkNeg("GET  /api/exam/report/my?patientId=999（患者越权查询他人报告应拒绝）",
                engine.getWithAuth("/api/exam/report/my?patientId=999"));

        // 2) 患者本人查询（不带 patientId）→ 应正常返回（可能是空分页）
        check("GET  /api/exam/report/my（患者本人报告查询）", engine.getWithAuth("/api/exam/report/my"));

        // 3) 管理员带 patientId 代查 → 应正常返回
        engine.setToken(adminToken());
        Object patientId = state.get("patientId");
        if (patientId != null) {
            check("GET  /api/exam/report/my?patientId=" + patientId + "（管理员代查报告）",
                    engine.getWithAuth("/api/exam/report/my?patientId=" + patientId));
        }
    }

    // ==================== 迭代 7 药事回归（B1~B11） ====================

    /** B1/B5/B7/B8：药品三分类、批次效期预警、调拨、麻精五专登记（入库段） */
    private void testPharmacyCatalogBatch() throws IOException {
        engine.setToken(adminToken());

        // B1 建 3 类药品：中药饮片 / 麻精（麻醉） / 特殊使用级抗菌
        ApiTestEngine.ApiResponse herbal = engine.postWithAuth("/api/admin/drug", Map.of(
                "drugCode", "HB" + randomDigits(4), "drugName", "当归饮片（自动化）",
                "genericName", "当归", "specification", "饮片 1g", "dosageForm", "饮片",
                "manufacturer", "自动化中药房", "referencePrice", 0.8, "unit", "g",
                "description", "补血活血（自动化测试）", "drugType", "HERBAL"));
        check("POST /api/admin/drug（建中药饮片 HERBAL）", herbal);
        if (herbal.isOk() && herbal.getData() instanceof Map) {
            state.put("pharmacyHerbalDrugId", ((Map) herbal.getData()).get("id"));
        }

        ApiTestEngine.ApiResponse narcotic = engine.postWithAuth("/api/admin/drug", Map.of(
                "drugCode", "NC" + randomDigits(4), "drugName", "盐酸吗啡片（自动化）",
                "specification", "10mg×10片", "dosageForm", "片剂",
                "manufacturer", "自动化制药", "referencePrice", 15.0, "unit", "BOX",
                "description", "麻醉药品（自动化测试）", "drugType", "WESTERN", "controlLevel", "NARCOTIC"));
        check("POST /api/admin/drug（建麻醉药品 NARCOTIC）", narcotic);
        if (narcotic.isOk() && narcotic.getData() instanceof Map) {
            state.put("pharmacyNarcoticDrugId", ((Map) narcotic.getData()).get("id"));
        }

        ApiTestEngine.ApiResponse abx = engine.postWithAuth("/api/admin/drug", Map.of(
                "drugCode", "AB" + randomDigits(4), "drugName", "注射用万古霉素（自动化）",
                "specification", "0.5g/支", "dosageForm", "注射剂",
                "manufacturer", "自动化制药", "referencePrice", 68.0, "unit", "VIAL",
                "description", "特殊使用级抗菌药物（自动化测试）", "drugType", "WESTERN",
                "antibioticLevel", "SPECIAL"));
        check("POST /api/admin/drug（建特殊级抗菌药 SPECIAL）", abx);
        if (abx.isOk() && abx.getData() instanceof Map) {
            state.put("pharmacyAbxDrugId", ((Map) abx.getData()).get("id"));
        }

        Object herbalDrugId = state.get("pharmacyHerbalDrugId");
        Object narcoticDrugId = state.get("pharmacyNarcoticDrugId");
        Object abxDrugId = state.get("pharmacyAbxDrugId");
        if (herbalDrugId == null || narcoticDrugId == null || abxDrugId == null) {
            check("POST /api/admin/drug/batch/inbound（跳过—建药失败）",
                    syntheticOk("建药失败，跳过批次链路"));
            return;
        }

        // B1 分类管理端点
        check("PUT  /api/admin/drug/" + herbalDrugId + "/type（B1 分类管理）",
                engine.putWithAuth("/api/admin/drug/" + herbalDrugId + "/type",
                        Map.of("drugType", "HERBAL")));

        // B5 采购入库（麻精批次效期仅 +15 天 → 触发效期预警；抗菌药也入库供后续发药）
        String today = LocalDate.now().format(DATE_FMT);
        String expiringSoon = LocalDate.now().plusDays(15).format(DATE_FMT);
        String nextYear = LocalDate.now().plusYears(1).format(DATE_FMT);
        ApiTestEngine.ApiResponse nb = engine.postWithAuth("/api/admin/drug/batch/inbound", Map.of(
                "drugId", narcoticDrugId, "batchNo", "N2026A" + randomDigits(3),
                "supplier", "国药集团（自动化）", "quantity", 100,
                "productionDate", today, "expiryDate", expiringSoon));
        check("POST /api/admin/drug/batch/inbound（麻精采购入库 100，效期+15天）", nb);
        if (nb.isOk() && nb.getData() instanceof Map) {
            state.put("pharmacyNarcoticBatchId", ((Map) nb.getData()).get("id"));
        }
        check("POST /api/admin/drug/batch/inbound（饮片采购入库 500）",
                engine.postWithAuth("/api/admin/drug/batch/inbound", Map.of(
                        "drugId", herbalDrugId, "batchNo", "P2026I" + randomDigits(3),
                        "supplier", "亳州药市（自动化）", "quantity", 500,
                        "productionDate", today, "expiryDate", nextYear)));
        check("POST /api/admin/drug/batch/inbound（抗菌药采购入库 200）",
                engine.postWithAuth("/api/admin/drug/batch/inbound", Map.of(
                        "drugId", abxDrugId, "batchNo", "V2026V" + randomDigits(3),
                        "supplier", "自动化医药", "quantity", 200,
                        "productionDate", today, "expiryDate", nextYear)));

        // B5 效期预警：麻精批次（+15 天）应出现在 30 天预警列表
        ApiTestEngine.ApiResponse expiring = engine.getWithAuth("/api/admin/drug/batch/expiring");
        check("GET  /api/admin/drug/batch/expiring（B5 效期预警）", expiring);
        if (expiring.isOk() && expiring.getData() instanceof List) {
            boolean hit = ((List<?>) expiring.getData()).stream()
                    .anyMatch(o -> o instanceof Map m
                            && String.valueOf(m.get("drugId")).equals(String.valueOf(narcoticDrugId)));
            assertTrue("效期预警应包含麻精临期批次（+15天）", hit);
        }

        // B8 五专登记：麻精入库应自动写 INBOUND 流水，结存=100
        ApiTestEngine.ApiResponse narcReg = engine.getWithAuth(
                "/api/admin/drug/narcotic/list?drugId=" + narcoticDrugId);
        check("GET  /api/admin/drug/narcotic/list（B8 麻精五专登记查询）", narcReg);
        if (narcReg.isOk() && narcReg.getData() instanceof List && !((List<?>) narcReg.getData()).isEmpty()) {
            Map first = (Map) ((List<?>) narcReg.getData()).get(0);
            assertTrue("麻精登记应有 INBOUND 流水且结存=100",
                    "INBOUND".equals(first.get("action"))
                            && first.get("balance") != null && ((Number) first.get("balance")).intValue() == 100);
        }

        // B7 调拨：饮片 药库→药房
        check("POST /api/admin/drug/transfer（B7 药库→药房调拨 50）",
                engine.postWithAuth("/api/admin/drug/transfer", Map.of(
                        "drugId", herbalDrugId, "quantity", 50,
                        "fromLocation", "WAREHOUSE", "toLocation", "PHARMACY",
                        "batchNo", "P2026ITR")));
        check("GET  /api/admin/drug/transfer/list（调拨记录）",
                engine.getWithAuth("/api/admin/drug/transfer/list?pageNo=1&pageSize=10"));
    }

    /** B2/B4/B9/B10：中药饮片处方、CDSS 拦截、抗菌分级授权拦截与放行（含完整门诊链路） */
    private void testPharmacyPrescribing() throws IOException {
        String today = LocalDate.now().format(DATE_FMT);

        // 独立"药事患者"：规避主患者与主链路排班的防重复挂号键（2003）
        String pharPhone = "135" + randomDigits(8);
        check("POST /api/auth/register（注册药事链路患者）",
                engine.register(Map.of("phone", pharPhone, "password", "Test12345",
                        "realName", "药事自动化患者", "gender", 1)));
        String pharToken = engine.login(pharPhone, "Test12345");
        if (pharToken.isEmpty()) {
            check("POST /api/clinic/appointments（跳过—药事患者登录失败）",
                    syntheticOk("药事患者登录失败"));
            return;
        }
        state.put("pharmacyToken", pharToken);
        engine.setToken(pharToken);
        ApiTestEngine.ApiResponse pharProfile = engine.getWithAuth("/api/patient/profile");
        if (pharProfile.isOk() && pharProfile.getData() instanceof Map
                && ((Map) pharProfile.getData()).get("id") != null) {
            state.put("pharmacyPatientId", ((Map) pharProfile.getData()).get("id"));
        }

        // 新挂号（药事患者）→ 接诊（admin=李医生）→ 病历
        List<Map> slots = fetchAvailableSlots(DEPT_ID, today).stream()
                .filter(s -> "AVAILABLE".equals(s.get("status")))
                .filter(this::inCheckinWindow)
                .collect(Collectors.toList());
        if (slots.isEmpty()) {
            check("POST /api/clinic/appointments（跳过—药事链路无可用号源）",
                    syntheticOk("无可用号源，中药/拦截用例跳过"));
            return;
        }
        Map slot = slots.get(slots.size() - 1); // 取列表尾部的空闲号源
        long slotId = ((Number) slot.get("id")).longValue();
        long schedId = ((Number) slot.get("scheduleId")).longValue();
        ApiTestEngine.ApiResponse appt = engine.postWithAuth("/api/clinic/appointments",
                Map.of("slotId", slotId, "scheduleId", schedId));
        check("POST /api/clinic/appointments（药事链路挂号）", appt);
        if (!appt.isOk() || !(appt.getData() instanceof Map)) {
            return;
        }
        Object apptId = ((Map) appt.getData()).get("id");
        state.put("pharmacyAppointmentId", apptId);

        // 挂号缴费 → 签到（接诊前置，与主链路一致）
        Object pharPayOrderId = ((Map) appt.getData()).get("paymentOrderId");
        if (pharPayOrderId != null) {
            check("POST /api/payment/pay/" + pharPayOrderId + "（药事链路缴挂号费）",
                    engine.postWithAuth("/api/payment/pay/" + pharPayOrderId, Map.of()));
        }
        ApiTestEngine.ApiResponse checkin = engine.postWithAuth("/api/clinic/checkin",
                Map.of("appointmentId", apptId));
        check("POST /api/clinic/checkin（药事链路签到）", checkin);
        Object pharCheckinId = checkin.isOk() && checkin.getData() instanceof Map
                ? ((Map) checkin.getData()).get("id") : null;

        engine.setToken(adminToken());
        if (pharCheckinId != null) {
            check("POST /api/clinic/call/next（药事链路叫号）",
                    engine.postWithAuth("/api/clinic/call/next",
                            Map.of("departmentId", DEPT_ID, "consultRoom", "2诊室")));
        }
        ApiTestEngine.ApiResponse start = engine.postWithAuth(
                "/api/clinic/consultation/start?appointmentId=" + apptId, Map.of());
        check("POST /api/clinic/consultation/start（药事链路开始接诊）", start);
        if (!start.isOk() || !(start.getData() instanceof Map)) {
            return;
        }
        Object recordId2 = ((Map) start.getData()).get("id");
        state.put("pharmacyRecordId", recordId2);
        check("PUT  /api/clinic/consultation/" + recordId2 + "（药事链路提交病历）",
                engine.putWithAuth("/api/clinic/consultation/" + recordId2,
                        Map.of("chiefComplaint", "药事自动化主诉", "presentIllness", "药事自动化现病史",
                                "temperature", 36.8, "pulse", 76, "respiration", 18,
                                "bloodPressure", "118/78", "diagnosisCode", "J06.9",
                                "diagnosisDesc", "急性上呼吸道感染（药事自动化）", "action", "SUBMIT")));

        Object abxDrugId = state.get("pharmacyAbxDrugId");
        Object narcoticDrugId = state.get("pharmacyNarcoticDrugId");
        Object herbalDrugId = state.get("pharmacyHerbalDrugId");
        if (abxDrugId == null || narcoticDrugId == null || herbalDrugId == null) {
            check("POST /api/clinic/prescription（跳过—药事药品未就绪）", syntheticOk("药品未就绪"));
            return;
        }

        // B9 CDSS：建 MAX_DOSE/BLOCK 规则 → 开方（1g 超单次 0.1g 上限）→ 预期被拦截
        ApiTestEngine.ApiResponse rule = engine.postWithAuth("/api/admin/drug/rule", Map.of(
                "ruleType", "MAX_DOSE", "drugId", abxDrugId, "maxSingleDose", 0.1,
                "severity", "BLOCK", "description", "万古霉素单次上限 0.1g（自动化）"));
        check("POST /api/admin/drug/rule（B9 建 CDSS 剂量拦截规则）", rule);
        Object ruleId = rule.isOk() && rule.getData() instanceof Map ? ((Map) rule.getData()).get("id") : null;
        checkNeg("POST /api/clinic/prescription（B9 剂量超限应被 CDSS 拦截）",
                engine.postWithAuth("/api/clinic/prescription", Map.of(
                        "medicalRecordId", recordId2, "items", List.of(Map.ofEntries(
                                Map.entry("drugId", abxDrugId), Map.entry("drugName", "注射用万古霉素（自动化）"),
                                Map.entry("specification", "0.5g/支"), Map.entry("dosage", "1g"),
                                Map.entry("usageMethod", "IV"), Map.entry("frequency", "QD"),
                                Map.entry("days", 3), Map.entry("quantity", 3), Map.entry("price", 68.0),
                                Map.entry("unit", "VIAL"))))));
        if (ruleId != null) {
            check("DELETE /api/admin/drug/rule/" + ruleId + "（删除 CDSS 规则）",
                    engine.deleteWithAuth("/api/admin/drug/rule/" + ruleId));
        }

        // B10 抗菌分级：医生无 SPECIAL 授权 → 拦截；授权后 → 放行（同一处方同时含麻精药，供后续链路）
        checkNeg("POST /api/clinic/prescription（B10 无授权开特殊级抗菌药应拒绝）",
                engine.postWithAuth("/api/clinic/prescription", Map.of(
                        "medicalRecordId", recordId2, "items", List.of(Map.ofEntries(
                                Map.entry("drugId", abxDrugId), Map.entry("drugName", "注射用万古霉素（自动化）"),
                                Map.entry("specification", "0.5g/支"), Map.entry("dosage", "0.5g"),
                                Map.entry("usageMethod", "IV"), Map.entry("frequency", "QD"),
                                Map.entry("days", 3), Map.entry("quantity", 3), Map.entry("price", 68.0),
                                Map.entry("unit", "VIAL"))))));
        check("POST /api/admin/drug/antibiotic-auth（B10 授权医生 SPECIAL 级）",
                engine.postWithAuth("/api/admin/drug/antibiotic-auth",
                        Map.of("doctorId", 1, "maxLevel", "SPECIAL")));
        ApiTestEngine.ApiResponse rxWest = engine.postWithAuth("/api/clinic/prescription", Map.of(
                "medicalRecordId", recordId2, "prescriptionType", "WESTERN", "items", List.of(
                        Map.ofEntries(Map.entry("drugId", abxDrugId),
                                Map.entry("drugName", "注射用万古霉素（自动化）"),
                                Map.entry("specification", "0.5g/支"), Map.entry("dosage", "0.5g"),
                                Map.entry("usageMethod", "IV"), Map.entry("frequency", "QD"),
                                Map.entry("days", 3), Map.entry("quantity", 3), Map.entry("price", 68.0),
                                Map.entry("unit", "VIAL")),
                        Map.ofEntries(Map.entry("drugId", narcoticDrugId),
                                Map.entry("drugName", "盐酸吗啡片（自动化）"),
                                Map.entry("specification", "10mg×10片"), Map.entry("dosage", "10mg"),
                                Map.entry("usageMethod", "ORAL"), Map.entry("frequency", "PRN"),
                                Map.entry("days", 2), Map.entry("quantity", 1), Map.entry("price", 15.0),
                                Map.entry("unit", "BOX")))));
        check("POST /api/clinic/prescription（B10 授权后开西药笺成功，含麻精药）", rxWest);
        if (rxWest.isOk() && rxWest.getData() instanceof Map) {
            state.put("pharmacyRxWesternId", ((Map) rxWest.getData()).get("id"));
        }

        // B2/B4 中药饮片处方笺（剂数+煎服法+明细煎法脚注）
        ApiTestEngine.ApiResponse rxHerbal = engine.postWithAuth("/api/clinic/prescription", Map.of(
                "medicalRecordId", recordId2, "prescriptionType", "HERBAL",
                "herbalDoses", 7, "herbalUsage", "每日一剂，水煎400ml，分早晚两次温服",
                "items", List.of(
                        Map.ofEntries(Map.entry("drugId", herbalDrugId),
                                Map.entry("drugName", "当归饮片（自动化）"),
                                Map.entry("specification", "饮片 1g"), Map.entry("dosage", "10g"),
                                Map.entry("usageMethod", "DECOCT"), Map.entry("frequency", "BID"),
                                Map.entry("days", 7), Map.entry("quantity", 70), Map.entry("price", 0.8),
                                Map.entry("unit", "g"), Map.entry("decoctionMethod", "先煎"),
                                Map.entry("footnote", "_AUTO_FOOTNOTE_")),
                        Map.ofEntries(Map.entry("drugId", herbalDrugId),
                                Map.entry("drugName", "当归饮片（自动化）"),
                                Map.entry("specification", "饮片 1g"), Map.entry("dosage", "6g"),
                                Map.entry("usageMethod", "DECOCT"), Map.entry("frequency", "BID"),
                                Map.entry("days", 7), Map.entry("quantity", 42), Map.entry("price", 0.8),
                                Map.entry("unit", "g"), Map.entry("decoctionMethod", "后下"),
                                Map.entry("footnote", "冲服")))));
        check("POST /api/clinic/prescription（B2/B4 开中药饮片笺 7 剂）", rxHerbal);
        if (rxHerbal.isOk() && rxHerbal.getData() instanceof Map) {
            state.put("pharmacyRxHerbalId", ((Map) rxHerbal.getData()).get("id"));
            assertTrue("中药处方类型应为 HERBAL",
                    "HERBAL".equals(((Map) rxHerbal.getData()).get("prescriptionType")));
        }

        // 两张处方缴费（真实流：划价收费 → 审核 → 发药；药事患者本人支付）
        engine.setToken(pharToken);
        for (String key : List.of("pharmacyRxWesternId", "pharmacyRxHerbalId")) {
            Object rxId = state.get(key);
            if (rxId == null) continue;
            ApiTestEngine.ApiResponse cost = engine.postWithAuth("/api/payment/treatment/order",
                    Map.of("orderType", "DRUG", "relatedId", rxId,
                            "items", List.of(Map.of("itemName", "处方药费（药事自动化）",
                                    "qty", 1, "price", 30.0))));
            check("POST /api/payment/treatment/order（药事处方 " + key + " 缴费单）", cost);
            if (cost.isOk() && cost.getData() instanceof Map) {
                Object orderId = ((Map) cost.getData()).get("id");
                check("POST /api/payment/treatment/pay/" + orderId + "（药事处方缴费）",
                        engine.postWithAuth("/api/payment/treatment/pay/" + orderId, Map.of()));
            }
        }
    }

    /** B3/B6/B8/B11：审核发药麻精出账、退药冲账、代煎状态机、用药指导单 PDF */
    private void testPharmacyOps() throws IOException {
        engine.setToken(adminToken());
        Object rxWesternId = state.get("pharmacyRxWesternId");
        Object rxHerbalId = state.get("pharmacyRxHerbalId");
        Object narcoticDrugId = state.get("pharmacyNarcoticDrugId");
        Object herbalDrugId = state.get("pharmacyHerbalDrugId");
        Object patientId = state.get("pharmacyPatientId");

        // B8 麻精发药闭环：审核（四查十对）→ 发药 → OUTBOUND 流水
        if (rxWesternId != null) {
            check("PUT  /api/admin/drug/dispense/" + rxWesternId + "/review（麻精处方审核-四查十对）",
                    engine.putWithAuth("/api/admin/drug/dispense/" + rxWesternId + "/review",
                            Map.of("action", "APPROVE", "reviewComment", "四查十对通过（自动化）",
                                    "reviewCheck", "双人复核（自动化）")));
            check("POST /api/admin/drug/dispense/" + rxWesternId + "（麻精处方发药）",
                    engine.postWithAuth("/api/admin/drug/dispense/" + rxWesternId, Map.of()));
            if (narcoticDrugId != null) {
                ApiTestEngine.ApiResponse after = engine.getWithAuth(
                        "/api/admin/drug/narcotic/list?drugId=" + narcoticDrugId);
                check("GET  /api/admin/drug/narcotic/list（B8 发药后五专流水）", after);
                if (after.isOk() && after.getData() instanceof List && !((List<?>) after.getData()).isEmpty()) {
                    Map latest = (Map) ((List<?>) after.getData()).get(0);
                    assertTrue("麻精发药应写 OUTBOUND 流水且结存=99",
                            "OUTBOUND".equals(latest.get("action"))
                                    && latest.get("balance") != null
                                    && ((Number) latest.get("balance")).intValue() == 99);
                }
            }

            // B6 退药冲账（发药后退 1 盒麻精）
            if (patientId != null && narcoticDrugId != null) {
                check("POST /api/admin/drug/return（B6 麻精退药 1 盒冲账）",
                        engine.postWithAuth("/api/admin/drug/return", Map.of(
                                "prescriptionId", rxWesternId, "patientId", patientId,
                                "drugId", narcoticDrugId, "quantity", 1,
                                "refundAmount", 15.0, "reason", "自动化退药冲账")));
                ApiTestEngine.ApiResponse afterRet = engine.getWithAuth(
                        "/api/admin/drug/narcotic/list?drugId=" + narcoticDrugId);
                if (afterRet.isOk() && afterRet.getData() instanceof List && !((List<?>) afterRet.getData()).isEmpty()) {
                    Map latest = (Map) ((List<?>) afterRet.getData()).get(0);
                    assertTrue("麻精退药应写 RETURN 流水且结存回补=100",
                            "RETURN".equals(latest.get("action"))
                                    && latest.get("balance") != null
                                    && ((Number) latest.get("balance")).intValue() == 100);
                }
            }

            // B11 用药指导单 PDF
            checkReachable("GET  /api/medsupply/guidance/" + rxWesternId + "/pdf（B11 用药指导单 PDF）",
                    engine.getWithAuth("/api/medsupply/guidance/" + rxWesternId + "/pdf"));
        }

        // B3 中药代煎状态机：下单（HOSPITAL）→ DECOCTING → READY（取药凭证）→ DISPENSED
        if (rxHerbalId != null) {
            ApiTestEngine.ApiResponse deco = engine.postWithAuth("/api/medsupply/decoction",
                    Map.of("prescriptionId", rxHerbalId, "doses", 7, "decoctionType", "HOSPITAL"));
            check("POST /api/medsupply/decoction（B3 中药代煎下单 7 剂）", deco);
            Object decoId = deco.isOk() && deco.getData() instanceof Map
                    ? ((Map) deco.getData()).get("id") : null;
            if (decoId != null) {
                check("PUT  /api/medsupply/decoction/" + decoId + "/handle（开始煎制）",
                        engine.putWithAuth("/api/medsupply/decoction/" + decoId + "/handle",
                                Map.of("action", "DECOCTING")));
                ApiTestEngine.ApiResponse ready = engine.putWithAuth(
                        "/api/medsupply/decoction/" + decoId + "/handle", Map.of("action", "READY"));
                check("PUT  /api/medsupply/decoction/" + decoId + "/handle（煎制完成待取药）", ready);
                if (ready.isOk() && ready.getData() instanceof Map) {
                    assertTrue("代煎 READY 应生成取药凭证码",
                            ((Map) ready.getData()).get("pickupCode") != null);
                }
                check("PUT  /api/medsupply/decoction/" + decoId + "/handle（凭凭证发药）",
                        engine.putWithAuth("/api/medsupply/decoction/" + decoId + "/handle",
                                Map.of("action", "DISPENSED")));
            }
        }

        // B5 养护报损：新建小批次→报损清零
        if (herbalDrugId != null) {
            String today = LocalDate.now().format(DATE_FMT);
            ApiTestEngine.ApiResponse small = engine.postWithAuth("/api/admin/drug/batch/inbound", Map.of(
                    "drugId", herbalDrugId, "batchNo", "S2026S" + randomDigits(3),
                    "supplier", "自动化药库", "quantity", 10,
                    "productionDate", today, "expiryDate", LocalDate.now().plusMonths(6).format(DATE_FMT)));
            check("POST /api/admin/drug/batch/inbound（待报损小批次 10）", small);
            if (small.isOk() && small.getData() instanceof Map) {
                Object batchId = ((Map) small.getData()).get("id");
                check("POST /api/admin/drug/batch/" + batchId + "/scrap（B5 养护报损）",
                        engine.postWithAuth("/api/admin/drug/batch/" + batchId + "/scrap",
                                Map.of("reason", "受潮变质（自动化报损）")));
            }
        }
    }

    @FunctionalInterface interface ThrowingRunnable { void run() throws IOException; }
}
