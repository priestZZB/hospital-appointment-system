package com.hospital.patient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import com.hospital.patient.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件上传接口
 */
@Slf4j
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf", "doc", "docx");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

    /** 上传文件（头像/证件等），返回访问 URL */
    @AuditLog(value = "上传文件", operationType = "INSERT")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "文件大小不能超过 10MB");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持的文件类型，仅允许 " + ALLOWED_EXTENSIONS);
            }
        } else {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "无法识别的文件类型");
        }
        return Result.ok(fileService.upload(file));
    }
}
