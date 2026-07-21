package com.hospital.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 科室保存 DTO（新增/编辑共用）
 */
@Data
public class DepartmentSaveDTO {

    @NotBlank(message = "科室名称不能为空")
    @Size(max = 100, message = "科室名称不能超过100个字")
    private String deptName;

    @NotBlank(message = "科室编码不能为空")
    @Size(max = 50, message = "科室编码不能超过50个字")
    private String deptCode;

    @Size(max = 500, message = "科室简介不能超过500个字")
    private String description;

    @Size(max = 100, message = "诊区位置不能超过100个字")
    private String location;

    @Size(max = 20, message = "联系电话不能超过20个字")
    private String phone;

    private Integer sortOrder;
}
