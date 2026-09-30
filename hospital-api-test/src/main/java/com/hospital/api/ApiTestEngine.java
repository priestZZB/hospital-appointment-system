package com.hospital.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * API 自动化测试引擎
 * <p>
 * 基于 OkHttp，封装 JSON 请求/响应、JWT 认证、文件上传。
 * 用于对迭代 1 + 迭代 2 的 42 个对外接口进行全量回归测试。
 *
 * <pre>
 * 使用示例：
 *   ApiTestEngine engine = new ApiTestEngine("http://localhost:28080");
 *   // 白名单接口
 *   engine.post("/api/auth/register", Map.of("phone","13800000001","password","abc12345","realName","测试","idCard","310101199001011234"));
 *   // 登录获取 Token
 *   String token = engine.login("13800000001", "abc12345");
 *   // 带认证的接口
 *   engine.getWithAuth("/api/patient/profile");
 * </pre>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ApiTestEngine {

    private final String baseUrl;
    private String token;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final boolean verbose;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public ApiTestEngine(String baseUrl) {
        this(baseUrl, false);
    }

    /**
     * @param verbose true 时在控制台打印每次请求的方法/路径/HTTP 状态/耗时/响应摘要
     */
    public ApiTestEngine(String baseUrl, boolean verbose) {
        this.baseUrl = baseUrl;
        this.verbose = verbose;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** 设置 JWT Token */
    public void setToken(String token) {
        this.token = token;
    }

    // ==================== HTTP 方法 ====================

    /** GET（无需认证） */
    public ApiResponse get(String path) throws IOException {
        Request request = new Request.Builder().url(fullUrl(path)).get().build();
        return execute(request);
    }

    /** GET（需认证） */
    public ApiResponse getWithAuth(String path) throws IOException {
        Request request = new Request.Builder().url(fullUrl(path)).get()
                .header("Authorization", bearer()).build();
        return execute(request);
    }

    /** GET（需认证，不解析 JSON，直接返回原始字节，用于 PDF 等二进制下载） */
    public ApiResponse getBytesWithAuth(String path) throws IOException {
        Request request = new Request.Builder().url(fullUrl(path)).get()
                .header("Authorization", bearer()).build();
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            int status = response.code();
            byte[] bytes = response.body() != null ? response.body().bytes() : new byte[0];
            long elapsed = System.currentTimeMillis() - start;
            boolean ok = status >= 200 && status < 300 && bytes.length > 0;
            String msg = "bytes=" + bytes.length;
            ApiResponse resp = new ApiResponse(status,
                    Map.of("code", ok ? 0 : -1, "message", msg), elapsed, bytes);
            if (verbose) {
                System.out.printf("    >> %-6s %s → HTTP %d | %s | %dms%n",
                        "GET", path, status, msg, elapsed);
            }
            return resp;
        }
    }

    /** POST（JSON，无需认证） */
    public ApiResponse post(String path, Object body) throws IOException {
        String json = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder().url(fullUrl(path))
                .post(RequestBody.create(json, JSON)).build();
        return execute(request);
    }

    /** POST（JSON，需认证） */
    public ApiResponse postWithAuth(String path, Object body) throws IOException {
        String json = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder().url(fullUrl(path))
                .header("Authorization", bearer())
                .post(RequestBody.create(json, JSON)).build();
        return execute(request);
    }

    /** PUT（JSON，需认证） */
    public ApiResponse putWithAuth(String path, Object body) throws IOException {
        String json = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder().url(fullUrl(path))
                .header("Authorization", bearer())
                .put(RequestBody.create(json, JSON)).build();
        return execute(request);
    }

    /** DELETE（需认证） */
    public ApiResponse deleteWithAuth(String path) throws IOException {
        Request request = new Request.Builder().url(fullUrl(path))
                .header("Authorization", bearer()).delete().build();
        return execute(request);
    }

    /** POST 文件上传（需认证） */
    public ApiResponse uploadWithAuth(String path, String fileField, File file) throws IOException {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(fileField, file.getName(),
                        RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .build();
        Request request = new Request.Builder().url(fullUrl(path))
                .header("Authorization", bearer()).post(body).build();
        return execute(request);
    }

    /** POST multipart 表单字段（需认证，无文件） */
    public ApiResponse multipartFieldsWithAuth(String path, java.util.Map<String, String> fields) throws IOException {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        if (fields != null) {
            fields.forEach(builder::addFormDataPart);
        }
        Request request = new Request.Builder().url(fullUrl(path))
                .header("Authorization", bearer())
                .post(builder.build())
                .build();
        return execute(request);
    }

    // ==================== 业务快捷方法 ====================

    /** 登录并获取 Token 字符串 */
    public String login(String phone, String password) throws IOException {
        Map<String, String> body = Map.of("phone", phone, "password", password);
        ApiResponse resp = post("/api/auth/login", body);
        if (resp.isOk()) {
            Map<String, Object> data = (Map<String, Object>) resp.getData();
            String t = (String) data.get("token");
            if (t == null) t = (String) data.get("accessToken"); // fallback
            this.token = t;
            return t;
        }
        throw new RuntimeException("登录失败: " + resp.getMessage());
    }

    /** 患者注册 */
    public ApiResponse register(Map<String, Object> body) throws IOException {
        return post("/api/auth/register", body);
    }

    // ==================== 内部方法 ====================

    private String fullUrl(String path) {
        if (path.startsWith("http")) return path;
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private ApiResponse execute(Request request) throws IOException {
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : null;
            int status = response.code();
            long elapsed = System.currentTimeMillis() - start;
            ApiResponse resp;
            if (body != null && !body.isEmpty()) {
                try {
                    resp = new ApiResponse(status, objectMapper.readValue(body, Map.class),
                            elapsed, body.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    resp = new ApiResponse(status, Map.of("code", -1, "message", body),
                            elapsed, body.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                resp = new ApiResponse(status, Map.of("code", -1, "message", "empty response"), elapsed, new byte[0]);
            }
            if (verbose) {
                System.out.printf("    >> %-6s %s → HTTP %d | code=%d | %s | %dms%n",
                        request.method(), request.url().encodedPath(), status,
                        resp.getCode(), resp.getMessage(), elapsed);
            }
            return resp;
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier {
        ApiResponse get() throws IOException;
    }

    /**
     * 统一 API 响应结构
     */
    public static class ApiResponse {
        private final int httpStatus;
        private final Map<String, Object> raw;
        private final long elapsedMs;
        private final byte[] rawBytes;

        public ApiResponse(int httpStatus, Map<String, Object> raw) {
            this(httpStatus, raw, 0L, new byte[0]);
        }

        public ApiResponse(int httpStatus, Map<String, Object> raw, long elapsedMs, byte[] rawBytes) {
            this.httpStatus = httpStatus;
            this.raw = raw;
            this.elapsedMs = elapsedMs;
            this.rawBytes = rawBytes;
        }

        public int getHttpStatus() { return httpStatus; }
        public long getElapsedMs() { return elapsedMs; }
        public byte[] getRawBytes() { return rawBytes; }
        public int getCode() { return raw.containsKey("code") ? ((Number) raw.get("code")).intValue() : -1; }
        public String getMessage() { return (String) raw.get("message"); }
        public Object getData() { return raw.get("data"); }
        public Map<String, Object> getRaw() { return raw; }

        public boolean isOk() { return getCode() == 0; }
        public boolean isHttpOk() { return httpStatus >= 200 && httpStatus < 300; }

        @Override
        public String toString() {
            return String.format("HTTP %d | code=%d | %s | %dms", httpStatus, getCode(), getMessage(), elapsedMs);
        }
    }
}
