package com.hospital.patient.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑患者档案 DTO
 */
@Data
public class UpdatePatientDTO {

    @Size(max = 50, message = "姓名长度不能超过 50 个字")
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String idCard;

    @Size(max = 50, message = "紧急联系人姓名长度不能超过 50 个字")
    private String emergencyContact;

    @Size(max = 20, message = "紧急联系人电话长度不能超过 20")
    private String emergencyPhone;
}
