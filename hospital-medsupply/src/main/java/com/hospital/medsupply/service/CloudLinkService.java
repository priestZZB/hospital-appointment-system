package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ExamReport;
import com.hospital.medsupply.entity.ImageSeries;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ExamReportMapper;
import com.hospital.medsupply.mapper.ImageSeriesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 云影像链接服务（D6 云影像分享）
 * <p>
 * 生成 8 位随机 code 存 Redis（key = cloud:link:{code}，value = applicationId，TTL 24h）；
 * 患者凭 code 查看影像序列列表与已发布的结构化报告（findings/conclusion）。
 * <p>
 * 接口安全模型：/view 挂 MEDSUPPLY_CLOUDLINK_VIEW 权限（患者已种该权限），
 * 未登录访问由网关统一拦截；不另设公开匿名端点（网关白名单由运维侧另行处理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudLinkService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ImageSeriesMapper seriesMapper;
    private final ExamReportMapper reportMapper;
    private final ExamApplicationMapper applicationMapper;

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "cloud:link:";

    /** 链接有效期 24 小时 */
    private static final Duration LINK_TTL = Duration.ofHours(24);

    /** code 长度 8 位 */
    private static final int CODE_LENGTH = 8;

    /** code 字符集（去除易混淆的 0/O、1/I/L） */
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成云影像链接：8 位随机 code → Redis（TTL 24h）
     *
     * @param applicationId 检查申请 ID
     * @param operatorId    操作人 ID
     * @return {code, url, applicationId, expireHours}
     */
    public Map<String, Object> createLink(Long applicationId, Long operatorId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "applicationId 不能为空");
        }
        ExamApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.EXAM_APPLICATION_NOT_FOUND);
        }

        // setIfAbsent 防撞码，极小概率冲突时重试
        String code = null;
        Boolean stored = null;
        for (int retry = 0; retry < 3 && !Boolean.TRUE.equals(stored); retry++) {
            code = generateCode();
            stored = stringRedisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + code, String.valueOf(applicationId), LINK_TTL);
        }
        if (!Boolean.TRUE.equals(stored)) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "云影像链接生成失败，请重试");
        }

        log.info("[云影像] 链接生成: applicationId={}, code={}, operatorId={}", applicationId, code, operatorId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("url", "/cloud-view/" + code);
        result.put("applicationId", applicationId);
        result.put("expireHours", LINK_TTL.toHours());
        return result;
    }

    /**
     * 解析云影像链接：校验 code → 返回影像序列列表 + 已发布报告内容
     * <p>
     * 影像列表始终返回；report 仅在报告状态为 PUBLISHED 时返回
     * {findings, conclusion, status}，否则为 null（草稿/待审核/驳回报告不外泄）。
     *
     * @param code 云影像 code
     */
    public Map<String, Object> resolve(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "code 不能为空");
        }
        String applicationIdStr = stringRedisTemplate.opsForValue().get(KEY_PREFIX + code);
        if (applicationIdStr == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "云影像链接不存在或已过期");
        }
        long applicationId;
        try {
            applicationId = Long.parseLong(applicationIdStr);
        } catch (NumberFormatException e) {
            log.warn("[云影像] 链接数据异常: code={}, value={}", code, applicationIdStr);
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "云影像链接无效");
        }

        // 影像序列列表（始终返回）
        List<ImageSeries> seriesList = seriesMapper.selectByApplication(applicationId);
        List<Map<String, Object>> series = new ArrayList<>();
        for (ImageSeries s : seriesList) {
            Map<String, Object> item = new HashMap<>();
            item.put("seriesNo", s.getSeriesNo());
            item.put("modality", s.getModality());
            item.put("imageCount", s.getImageCount());
            series.add(item);
        }

        // 仅已发布报告返回结构化内容
        Map<String, Object> reportView = null;
        ExamReport report = reportMapper.selectByApplicationId(applicationId);
        if (report != null && "PUBLISHED".equals(report.getStatus())) {
            reportView = new HashMap<>();
            reportView.put("findings", report.getFindings());
            reportView.put("conclusion", report.getConclusion());
            reportView.put("status", report.getStatus());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("applicationId", applicationId);
        result.put("series", series);
        result.put("report", reportView);
        return result;
    }

    /** 生成 8 位随机 code */
    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
