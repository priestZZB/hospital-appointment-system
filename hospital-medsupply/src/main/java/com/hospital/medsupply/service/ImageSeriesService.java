package com.hospital.medsupply.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.medsupply.entity.ExamApplication;
import com.hospital.medsupply.entity.ImageSeries;
import com.hospital.medsupply.mapper.ExamApplicationMapper;
import com.hospital.medsupply.mapper.ImageSeriesMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 影像序列服务（D2 影像上传 / PACS viewer 取图）
 * <p>
 * 影像文件写入本地磁盘（pacs.storage-root，与项目 uploadPath 本地存储模式一致），
 * object_keys 以 JSON 数组形式存 CLOB（相对路径，保持帧顺序）；PACS viewer 按序号取图。
 * 历史说明：原设计为 MinIO（bucket 沿用 patient-service），但开源版 MinIO 服务端
 * 已归档下架且本机无实例，故改为本地磁盘存储，接口契约与 object_keys 结构不变。
 * <p>
 * 上传大小限制：单张影像 ≤ 5MB（服务内校验），整体 multipart 上限依赖
 * 网关与容器的 spring.servlet.multipart.max-file-size / max-request-size 配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSeriesService {

    private final ImageSeriesMapper seriesMapper;
    private final ExamApplicationMapper applicationMapper;
    private final ObjectMapper objectMapper;

    /** 影像存储根目录（bootstrap.yml pacs.storage-root） */
    @Value("${pacs.storage-root:./data/pacs}")
    private String storageRoot;

    /** 单张影像大小上限 5MB */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 单序列影像张数上限（毕设简化，防止误传超大请求） */
    private static final int MAX_IMAGES_PER_SERIES = 64;

    /** 默认检查类别 */
    private static final String DEFAULT_MODALITY = "DX";

    /**
     * 上传影像序列：逐张写入 "{storage-root}/pacs/{applicationId}/{seriesNo}/{index}.jpg"，
     * 全部成功后插序列记录（image_count=文件数，object_keys=JSON 数组）。
     * <p>
     * 已写盘文件在 DB 插入失败时不回删（毕设简化，残留文件由磁盘清理策略处理）。
     *
     * @param applicationId 检查申请 ID
     * @param modality      检查类别（空则默认 DX）
     * @param description   序列描述（可选）
     * @param images        影像文件列表（单张 ≤ 5MB）
     * @param operatorId    上传人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ImageSeries upload(Long applicationId, String modality, String description,
                              List<MultipartFile> images, Long operatorId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "applicationId 不能为空");
        }
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "影像文件不能为空");
        }
        if (images.size() > MAX_IMAGES_PER_SERIES) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "单次最多上传 " + MAX_IMAGES_PER_SERIES + " 张影像");
        }
        ExamApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCodeEnum.EXAM_APPLICATION_NOT_FOUND);
        }

        ImageSeries series = new ImageSeries();
        series.setSeriesNo(generateSeriesNo());
        series.setApplicationId(applicationId);
        series.setPatientId(application.getPatientId());
        series.setModality(modality == null || modality.isBlank() ? DEFAULT_MODALITY : modality);
        series.setDescription(description);
        series.setUploadBy(operatorId);

        List<String> objectKeys = new ArrayList<>();
        try {
            int index = 0;
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                if (file.getSize() > MAX_IMAGE_SIZE) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                            "单张影像不能超过 5MB：" + file.getOriginalFilename());
                }
                String objectKey = buildObjectKey(applicationId, series.getSeriesNo(), index);
                Path target = resolvePath(objectKey);
                Files.createDirectories(target.getParent());
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                objectKeys.add(objectKey);
                index++;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[影像] 上传失败: applicationId={}, error={}", applicationId, e.getMessage());
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "影像上传失败，存储写入异常");
        }
        if (objectKeys.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未收到有效影像文件");
        }

        series.setImageCount(objectKeys.size());
        try {
            series.setObjectKeys(objectMapper.writeValueAsString(objectKeys));
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "影像索引序列化失败");
        }
        seriesMapper.insert(series);
        log.info("[影像] 序列上传成功: seriesId={}, seriesNo={}, applicationId={}, count={}, modality={}",
                series.getId(), series.getSeriesNo(), applicationId, objectKeys.size(), series.getModality());
        return seriesMapper.selectById(series.getId());
    }

    /**
     * PACS viewer 取图：按序列内序号从本地磁盘读取字节流
     *
     * @param seriesId 序列 ID
     * @param index    帧序号（0 起）
     */
    public byte[] getImage(Long seriesId, int index) {
        ImageSeries series = requireSeries(seriesId);
        List<String> keys = parseObjectKeys(series.getObjectKeys());
        if (index < 0 || index >= keys.size()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "影像序号超出范围：0 ~ " + (keys.size() - 1));
        }
        try {
            Path target = resolvePath(keys.get(index));
            if (!Files.exists(target)) {
                throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "影像文件已丢失");
            }
            return Files.readAllBytes(target);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[影像] 读取失败: seriesId={}, index={}, error={}", seriesId, index, e.getMessage());
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "影像读取失败，存储读取异常");
        }
    }

    /** 影像序列详情（含 object_keys 供 viewer 预取） */
    public ImageSeries getById(Long seriesId) {
        return requireSeries(seriesId);
    }

    /** 按检查申请查询影像序列列表 */
    public List<ImageSeries> listByApplication(Long applicationId) {
        return seriesMapper.selectByApplication(applicationId);
    }

    /** 影像序列分页（检查类别可选） */
    public Map<String, Object> listByPage(String modality, int pageNo, int pageSize) {
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        List<ImageSeries> records = seriesMapper.selectByPage(modality, offset, pageSize);
        long total = seriesMapper.countPage(modality);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", pageNo);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 删除序列（物理删除元数据，磁盘文件由清理策略处理） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long seriesId) {
        requireSeries(seriesId);
        seriesMapper.delete(seriesId);
        log.info("[影像] 序列删除: seriesId={}", seriesId);
    }

    /** 对象 key：pacs/{applicationId}/{seriesNo}/{index}.jpg */
    private String buildObjectKey(Long applicationId, String seriesNo, int index) {
        return "pacs/" + applicationId + "/" + seriesNo + "/" + index + ".jpg";
    }

    /** 相对 key → 磁盘绝对路径（统一收敛在 storageRoot 下，防路径穿越） */
    private Path resolvePath(String objectKey) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "非法影像路径");
        }
        return target;
    }

    /** 影像号：IM + yyyyMMdd + 6 位随机数 */
    private String generateSeriesNo() {
        return "IM" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** 解析 object_keys JSON 数组 */
    private List<String> parseObjectKeys(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("[影像] object_keys 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private ImageSeries requireSeries(Long seriesId) {
        if (seriesId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_MISSING, "seriesId 不能为空");
        }
        ImageSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "影像序列不存在");
        }
        return series;
    }
}
