package com.hospital.patient.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.patient.dto.AllergyDTO;
import com.hospital.patient.dto.UpdatePatientDTO;
import com.hospital.patient.entity.Allergy;
import com.hospital.patient.entity.Patient;
import com.hospital.patient.mapper.AllergyMapper;
import com.hospital.patient.mapper.PatientMapper;
import com.hospital.patient.mapper.VisitCardMapper;
import com.hospital.patient.vo.AllergyVO;
import com.hospital.patient.vo.PatientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PatientService / AllergyService 单元测试
 * <p>
 * 覆盖：查询档案、编辑档案、新增过敏史。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService 单元测试")
class PatientServiceTest {

    @Mock private PatientMapper patientMapper;
    @Mock private VisitCardMapper visitCardMapper;
    @Mock private AllergyMapper allergyMapper;

    @InjectMocks private PatientService patientService;
    @InjectMocks private AllergyService allergyService;

    private static final Long USER_ID = 100L;
    private static final Long PATIENT_ID = 200L;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(PATIENT_ID);
            return 1;
        }).when(patientMapper).insert(any(Patient.class));
        lenient().doAnswer(inv -> {
            Allergy a = inv.getArgument(0);
            a.setId(300L);
            return 1;
        }).when(allergyMapper).insert(any(Allergy.class));
    }

    // ==================== 查询档案 ====================

    @Nested
    @DisplayName("查询档案")
    class QueryProfile {

        @Test
        @DisplayName("正常查询患者档案")
        void testGetProfileSuccess() {
            when(patientMapper.selectByUserId(USER_ID)).thenReturn(buildPatient());

            PatientVO result = patientService.getProfile(USER_ID);

            assertNotNull(result);
            assertEquals(PATIENT_ID, result.getId());
            assertEquals(USER_ID, result.getUserId());
            assertEquals("张三", result.getName());
            assertEquals(0, result.getVerifyStatus());
        }

        @Test
        @DisplayName("档案不存在应抛出异常")
        void testGetProfileNotFound() {
            when(patientMapper.selectByUserId(USER_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> patientService.getProfile(USER_ID));
            assertTrue(ex.getMessage().contains("患者档案不存在"));
        }
    }

    // ==================== 编辑档案 ====================

    @Nested
    @DisplayName("编辑档案")
    class UpdateProfile {

        @Test
        @DisplayName("正常编辑患者档案")
        void testUpdateProfileSuccess() {
            Patient patient = buildPatient();
            Patient afterUpdate = buildPatient();
            afterUpdate.setName("李四");
            afterUpdate.setGender(1);
            // 第1次: 查现有档案; 第2次: update 后重新查询
            when(patientMapper.selectByUserId(USER_ID)).thenReturn(patient, afterUpdate);
            lenient().when(patientMapper.update(any(Patient.class))).thenReturn(1);

            UpdatePatientDTO dto = new UpdatePatientDTO();
            dto.setName("李四");
            dto.setGender(1);

            PatientVO result = patientService.updateProfile(USER_ID, dto);

            assertNotNull(result);
            assertEquals("李四", result.getName());
            assertEquals(1, result.getGender());
        }
    }

    // ==================== 新增过敏史 ====================

    @Nested
    @DisplayName("过敏史")
    class AllergyTests {

        @Test
        @DisplayName("正常新增过敏史")
        void testAddAllergySuccess() {
            when(patientMapper.selectByUserId(USER_ID)).thenReturn(buildPatient());
            lenient().when(allergyMapper.insert(any(Allergy.class))).thenReturn(1);

            AllergyDTO dto = new AllergyDTO();
            dto.setAllergen("青霉素");
            dto.setReactionType("RASH");
            dto.setSeverity("MILD");

            AllergyVO result = allergyService.add(USER_ID, dto);

            assertNotNull(result);
            assertEquals("青霉素", result.getAllergen());
            assertEquals("RASH", result.getReactionType());
        }

        @Test
        @DisplayName("查询过敏史列表")
        void testListAllergiesSuccess() {
            when(patientMapper.selectByUserId(USER_ID)).thenReturn(buildPatient());
            Allergy allergy = new Allergy();
            allergy.setId(1L);
            allergy.setPatientId(PATIENT_ID);
            allergy.setAllergen("青霉素");
            allergy.setSource("PATIENT");
            when(allergyMapper.selectByPatientId(PATIENT_ID)).thenReturn(List.of(allergy));

            List<AllergyVO> list = allergyService.listByUserId(USER_ID);

            assertNotNull(list);
            assertEquals(1, list.size());
            assertEquals("青霉素", list.get(0).getAllergen());
        }
    }

    // ==================== 辅助 ====================

    private Patient buildPatient() {
        Patient p = new Patient();
        p.setId(PATIENT_ID);
        p.setUserId(USER_ID);
        p.setName("张三");
        p.setGender(0);
        p.setPhone("13800001111");
        p.setVerifyStatus(0);
        return p;
    }
}
