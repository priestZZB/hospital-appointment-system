package com.hospital.medsupply.controller;

import com.hospital.common.annotation.AuditLog;
import com.hospital.common.result.Result;
import com.hospital.medsupply.entity.ExamItem;
import com.hospital.medsupply.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/exam")
@RequiredArgsConstructor
public class ExamItemController {

    private final ExamService examService;

    @GetMapping("/item")
    public Result<List<ExamItem>> list(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String itemType) {
        return Result.ok(examService.itemList(keyword, itemType));
    }

    @PostMapping("/item")
    @AuditLog(value = "创建检查项目", operationType = "INSERT")
    public Result<ExamItem> create(@RequestBody ExamItem item) {
        return Result.ok(examService.createItem(item));
    }
}
