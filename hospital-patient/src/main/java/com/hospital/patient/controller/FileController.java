package com.hospital.patient.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.result.Result;
import com.hospital.patient.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口
 */
@Slf4j
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 上传文件（头像/证件等），返回访问 URL */
    @AuditLog(value = "上传文件", operationType = "INSERT")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.upload(file));
    }
}
