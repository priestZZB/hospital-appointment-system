package com.hospital.clinic.service;

import cn.hutool.core.lang.UUID;
import com.hospital.clinic.entity.Doctor;
import com.hospital.clinic.entity.MedicalCertificate;
import com.hospital.clinic.mapper.DoctorMapper;
import com.hospital.clinic.mapper.MedicalCertificateMapper;
import com.hospital.clinic.vo.MedicalCertificateVO;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.feign.PatientFeignClient;
import com.hospital.common.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 医疗证明服务：诊断证明 / 病假条 / 转诊单 / 医疗建议，支持 PDF 下载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalCertificateService {

    private static final java.util.Set<String> CERT_TYPES =
            java.util.Set.of("DIAGNOSIS", "SICK_LEAVE", "REFERRAL", "MEDICAL_ADVICE");

    private final MedicalCertificateMapper certificateMapper;
    private final DoctorMapper doctorMapper;
    private final PatientFeignClient patientFeignClient;

    @Transactional(rollbackFor = Exception.class)
    public MedicalCertificateVO createCertificate(Long userId, MedicalCertificate cert) {
        Doctor doctor = requireDoctor(userId);
        if (cert.getPatientId() == null || cert.getCertType() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "患者与证明类型不能为空");
        }
        if (!CERT_TYPES.contains(cert.getCertType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持的证明类型");
        }
        cert.setCertNo("CERT" + UUID.fastUUID().toString().substring(0, 8).toUpperCase());
        cert.setDoctorId(doctor.getId());
        cert.setStatus("ISSUED");
        certificateMapper.insert(cert);
        log.info("[证明] 开具证明: certId={}, type={}, patientId={}", cert.getId(), cert.getCertType(), cert.getPatientId());
        return getCertificate(cert.getId(), userId);
    }

    public MedicalCertificateVO getCertificate(Long certId, Long userId) {
        MedicalCertificateVO vo = certificateMapper.selectList(null, null, null)
                .stream().filter(v -> v.getId().equals(certId)).findFirst().orElse(null);
        if (vo == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "证明不存在");
        }
        checkViewPermission(vo, userId);
        return vo;
    }

    public List<MedicalCertificateVO> listCertificates(Long userId, Long patientId, String certType) {
        Long doctorId = null;
        if (UserContext.isPatient()) {
            patientId = resolvePatientId(userId);
        } else if (!UserContext.isAdminOrSuperAdmin()) {
            Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor != null) {
                doctorId = doctor.getId();
            }
        }
        return certificateMapper.selectList(doctorId, patientId, certType);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelCertificate(Long userId, Long certId) {
        MedicalCertificate cert = certificateMapper.selectById(certId);
        if (cert == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "证明不存在");
        }
        Doctor doctor = doctorMapper.selectByUserId(userId);
        boolean admin = UserContext.isAdminOrSuperAdmin();
        if (!admin && (doctor == null || !Objects.equals(doctor.getId(), cert.getDoctorId()))) {
            throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "仅开具医生可作废证明");
        }
        certificateMapper.cancel(certId);
        log.info("[证明] 作废证明: certId={}", certId);
    }

    /** 生成证明 PDF 字节数组 */
    public byte[] generatePdf(Long certId, Long userId) {
        MedicalCertificateVO vo = getCertificate(certId, userId);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType0Font fontChinese = loadChineseFont(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = 750;
                // 标题
                cs.beginText();
                if (fontChinese != null) { cs.setFont(fontChinese, 22); } else { cs.setFont(PDType1Font.HELVETICA_BOLD, 22); }
                cs.newLineAtOffset(170, y);
                cs.showText(certTypeTitle(vo.getCertType()));
                cs.endText();
                y -= 40;

                cs.moveTo(50, y); cs.lineTo(550, y); cs.stroke();
                y -= 30;

                cs.beginText();
                if (fontChinese != null) { cs.setFont(fontChinese, 12); } else { cs.setFont(PDType1Font.HELVETICA, 12); }
                cs.newLineAtOffset(60, y);
                cs.showText("证明编号: " + vo.getCertNo());
                y -= 24;
                cs.newLineAtOffset(0, -24);
                cs.showText("患者ID: " + vo.getPatientId());
                y -= 24;
                cs.newLineAtOffset(0, -24);
                cs.showText("开具医生: " + (vo.getDoctorName() != null ? vo.getDoctorName() : ""));
                y -= 24;
                cs.newLineAtOffset(0, -24);
                if (vo.getStartDate() != null) {
                    cs.showText("起始日期: " + vo.getStartDate());
                    y -= 24;
                    cs.newLineAtOffset(0, -24);
                }
                if (vo.getDays() != null) {
                    cs.showText("建议天数: " + vo.getDays());
                    y -= 24;
                    cs.newLineAtOffset(0, -24);
                }
                cs.endText();
                y -= 20;

                // 内容（多行）
                cs.beginText();
                if (fontChinese != null) { cs.setFont(fontChinese, 12); } else { cs.setFont(PDType1Font.HELVETICA, 12); }
                cs.newLineAtOffset(60, y);
                String content = vo.getContent() != null ? vo.getContent() : "";
                for (String line : content.split("\n")) {
                    if (line.length() > 36) {
                        line = line.substring(0, 34) + "…";
                    }
                    cs.showText(line);
                    cs.newLineAtOffset(0, -20);
                }
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("[证明] PDF 生成成功: certId={}, size={}", certId, baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[证明] PDF 生成失败: certId={}", certId, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "PDF 生成失败");
        }
    }

    private void checkViewPermission(MedicalCertificateVO vo, Long userId) {
        if (UserContext.isAdminOrSuperAdmin()) {
            return;
        }
        if (UserContext.isDoctor()) {
            Doctor doctor = doctorMapper.selectByUserId(userId);
            if (doctor != null && Objects.equals(doctor.getId(), vo.getDoctorId())) {
                return;
            }
        }
        Long myPatientId = resolvePatientId(userId);
        if (myPatientId != null && myPatientId.equals(vo.getPatientId())) {
            return;
        }
        throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "无权查看该证明");
    }

    private Doctor requireDoctor(Long userId) {
        Doctor doctor = doctorMapper.selectByUserId(userId);
        if (doctor == null) {
            throw new BusinessException(ErrorCodeEnum.DOCTOR_NOT_FOUND);
        }
        return doctor;
    }

    private Long resolvePatientId(Long userId) {
        try {
            Map<String, Object> info = patientFeignClient.getByUserId(userId);
            if (info == null || info.get("id") == null) {
                return null;
            }
            return Long.valueOf(info.get("id").toString());
        } catch (Exception e) {
            return null;
        }
    }

    private PDType0Font loadChineseFont(PDDocument document) {
        try {
            return PDType0Font.load(document, new File("C:/Windows/Fonts/simsun.ttc"));
        } catch (Exception e) {
            try {
                return PDType0Font.load(document, new File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
            } catch (Exception e2) {
                log.warn("[证明] 未找到系统 CJK 字体，中文可能无法显示", e2);
                return null;
            }
        }
    }

    private String certTypeTitle(String type) {
        return switch (type == null ? "" : type) {
            case "SICK_LEAVE" -> "医院病假证明";
            case "REFERRAL" -> "医院转诊证明";
            case "MEDICAL_ADVICE" -> "医疗建议书";
            default -> "医院诊断证明";
        };
    }
}
