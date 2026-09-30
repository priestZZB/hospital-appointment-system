package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.interceptor.UserContext;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ImageSeries;
import com.hospital.medsupply.service.ImageSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 影像序列接口（D2，检查技师上传，医生/技师查看）
 * <p>
 * multipart 上传影像序列至 MinIO；PACS viewer 按序号取图（image/jpeg 字节流）。
 * 上传大小限制：单张 ≤ 5MB（服务内校验），整体上限依赖网关/容器 multipart 配置。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/exam/image")
@RequiredArgsConstructor
public class ImageSeriesController {

    private final ImageSeriesService imageSeriesService;

    /** 上传影像序列（multipart: images 多文件，modality/description 可选） */
    @AuditLog(value = "影像序列上传", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_UPLOAD)
    @PostMapping("/upload/{applicationId}")
    public Result<ImageSeries> upload(@PathVariable("applicationId") Long applicationId,
                                      @RequestParam("images") List<MultipartFile> images,
                                      @RequestParam(value = "modality", required = false) String modality,
                                      @RequestParam(value = "description", required = false) String description) {
        requireExamTechOrAdmin();
        return Result.ok(imageSeriesService.upload(applicationId, modality, description,
                images, UserContext.getUserId()));
    }

    /** 影像序列详情 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_QUERY)
    @GetMapping("/series/{id}")
    public Result<ImageSeries> detail(@PathVariable("id") Long id) {
        return Result.ok(imageSeriesService.getById(id));
    }

    /** PACS viewer 取图：按序列内序号返回影像字节流（image/jpeg） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_QUERY)
    @GetMapping("/series/{id}/image/{index}")
    public ResponseEntity<byte[]> image(@PathVariable("id") Long id,
                                        @PathVariable("index") int index) {
        byte[] data = imageSeriesService.getImage(id, index);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(data.length)
                .body(data);
    }

    /** 按检查申请查询影像序列列表 */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_QUERY)
    @GetMapping("/list")
    public Result<List<ImageSeries>> list(@RequestParam("applicationId") Long applicationId) {
        return Result.ok(imageSeriesService.listByApplication(applicationId));
    }

    /** 影像序列分页（检查类别可选） */
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_QUERY)
    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(value = "modality", required = false) String modality,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return Result.ok(imageSeriesService.listByPage(modality, pageNo, pageSize));
    }

    /** 删除影像序列（逻辑上移除元数据，MinIO 对象由生命周期策略清理） */
    @AuditLog(value = "影像序列删除", operationType = "DELETE")
    @RequiresPermission(PermissionConstant.MEDSUPPLY_IMAGE_UPLOAD)
    @DeleteMapping("/series/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        requireExamTechOrAdmin();
        imageSeriesService.delete(id);
        return Result.ok();
    }

    private void requireExamTechOrAdmin() {
        if (!UserContext.isExamTechOrAdmin()) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅检查技师或管理员可执行此操作");
        }
    }
}
