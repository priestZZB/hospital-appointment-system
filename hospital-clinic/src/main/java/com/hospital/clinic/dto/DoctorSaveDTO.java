package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 医生保存 DTO（新增/编辑共用）
 */
@Data
public class DoctorSaveDTO {

    private Long userId;

    @NotBlank(message = "医生姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50个字")
    private String name;

    private Integer gender;

    @Size(max = 20, message = "联系电话不能超过20个字")
    private String phone;

    @NotNull(message = "所属科室不能为空")
    private Long departmentId;

    @NotBlank(message = "职称不能为空")
    private String title;

    @Size(max = 255, message = "专长标签不能超过255个字")
    private String specialty;

    private String introduction;
}
