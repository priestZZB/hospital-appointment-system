package com.hospital.patient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 文件上传服务（MinIO）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final com.hospital.patient.config.MinioConfig minioConfig;

    /**
     * 上传文件到 MinIO，返回访问 URL
     *
     * @param file 上传的文件
     * @return 文件访问 URL
     */
    public String upload(MultipartFile file) {
        String objectName = "patient/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            String url = minioConfig.getEndpoint() + "/" + minioConfig.getBucket() + "/" + objectName;
            log.info("[文件] 上传成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("[文件] 上传失败: {}", e.getMessage());
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "文件上传失败，MinIO 服务不可用");
        }
    }
}
