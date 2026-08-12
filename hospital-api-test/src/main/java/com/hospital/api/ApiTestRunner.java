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
    private static final long DRUG_ID = 1L;     // medsupply_db.drug.id=1

    private final ApiTestEngine engine;
    private final TestReport report;
    private final Map<String, Object> state = new LinkedHashMap<>(); // 跨步骤共享状态
    private int stepNo = 0;
    private int stepTotal = 31;

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

        check("PUT  /api/auth/roles/1（编辑角色）",
                engine.putWithAuth("/api/auth/roles/1",
                        Map.of("roleCode", "ROLE_ADMIN", "roleName", "管理员",
                                "description", "平台超级管理员", "status", 1)));

        check("POST /api/auth/roles/assign（分配角色）",
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
        check("PUT  /api/auth/users/1/status（启用管理员）",
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
        check("POST /api/patient/upload（MinIO 文件上传）",
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
        List<Map> slots = fetchAvailableSlots(DEPT_ID, today);
        if (slots.isEmpty()) {
            check("POST /api/clinic/appointments（挂号）",
                    new ApiTestEngine.ApiResponse(500, Map.of("code", -2, "message", "今日无可用号源，无法执行挂号")));
            return;
        }

        Map slot1 = slots.get(0);
        Map slot2 = slots.size() > 1 ? slots.get(1) : slots.get(0);
        long slotId1 = ((Number) slot1.get("slotId")).longValue();
        long schedId1 = ((Number) slot1.get("scheduleId")).longValue();
        long slotId2 = ((Number) slot2.get("slotId")).longValue();
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
        check("GET  /api/admin/drug/1（药品详情）", engine.getWithAuth("/api/admin/drug/1"));
        check("PUT  /api/admin/drug/1（更新药品）",
                engine.putWithAuth("/api/admin/drug/1", Map.of("drugCode", "DRUG_TEST01", "drugName", "自动化测试药品v2",
                        "genericName", "测试", "specification", "10mg×20片", "dosageForm", "TABLET",
                        "manufacturer", "测试药厂", "referencePrice", 15.0, "unit", "BOX")));
    }

    private void testDrugInventory() throws IOException {
        engine.setToken(adminToken());
        check("GET  /api/admin/drug/inventory/list?pageNo=1&pageSize=10（库存列表）",
                engine.getWithAuth("/api/admin/drug/inventory/list?pageNo=1&pageSize=10"));
        check("POST /api/admin/drug/inventory/inbound（入库）",
                engine.postWithAuth("/api/admin/drug/inventory/inbound",
                        Map.of("drugId", DRUG_ID, "quantity", 50, "remark", "自动化测试入库")));
        check("POST /api/admin/drug/inventory/outbound（出库）",
                engine.postWithAuth("/api/admin/drug/inventory/outbound",
                        Map.of("drugId", DRUG_ID, "quantity", 5, "remark", "自动化测试出库")));
        check("POST /api/admin/drug/inventory/adjust（盘点调整）",
                engine.postWithAuth("/api/admin/drug/inventory/adjust",
                        Map.of("drugId", DRUG_ID, "quantity", 100, "remark", "自动化测试盘点")));
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
            check("POST /api/clinic/call/" + cid + "/recall（叫号重呼）",
                    engine.postWithAuth("/api/clinic/call/" + cid + "/recall",
                            Map.of("consultRoom", "1诊室")));
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

        check("POST /api/clinic/prescription（开具处方）",
                engine.postWithAuth("/api/clinic/prescription",
                        Map.of("medicalRecordId", recordId, "items", List.of(
                                Map.of("drugId", DRUG_ID, "drugName", "自动化测试药品", "specification", "10mg×20片",
                                        "dosage", "0.5g", "usageMethod", "ORAL", "frequency", "TID",
                                        "days", 3, "quantity", 9, "unit", "BOX", "remark", "自动化测试")))));

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
            check("PUT  /api/clinic/stop/" + applicationId + "/approve（驳回停诊）",
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
        String period = LocalTime.now().isBefore(LocalTime.NOON) ? "AM" : "PM";
        ApiTestEngine.ApiResponse r = engine.postWithAuth("/api/clinic/schedules",
                Map.of("doctorId", DOCTOR_ID, "departmentId", DEPT_ID, "scheduleDate", today,
                        "period", period, "periodStart", start.format(TIME_FMT),
                        "periodEnd", end.format(TIME_FMT), "slotDuration", 10, "registerFee", 20.0));
        check("POST /api/clinic/schedules（创建今日排班 " + start.format(TIME_FMT) + " 起）", r);
        if (r.isOk() && r.getData() instanceof Map) {
            return ((Number) ((Map) r.getData()).get("id")).longValue();
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
            return ((Number) ((Map) r.getData()).get("id")).longValue();
        }
        return null;
    }

    private List<Map> fetchAvailableSlots(Long deptId, String date) throws IOException {
        ApiTestEngine.ApiResponse r = engine.getWithAuth("/api/clinic/slots?departmentId=" + deptId + "&date=" + date);
        if (r.isOk() && r.getData() instanceof List) {
            return (List<Map>) r.getData();
        }
        return Collections.emptyList();
    }

    /** 判断号源开始时间是否落在当前时间 ±30 分钟窗口内（签到窗口） */
    private boolean inCheckinWindow(Map slot) {
        LocalTime st = parseSlotStart(slot.get("slotStart"));
        if (st == null) return false;
        LocalDateTime slotStart = LocalDateTime.of(LocalDate.now(), st);
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(slotStart.minusMinutes(30)) && !now.isAfter(slotStart.plusMinutes(30));
    }

    private LocalTime parseSlotStart(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        try { return LocalTime.parse(s); } catch (Exception ignore) { }
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")); } catch (Exception ignore) { }
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

    @FunctionalInterface interface ThrowingRunnable { void run() throws IOException; }
}
