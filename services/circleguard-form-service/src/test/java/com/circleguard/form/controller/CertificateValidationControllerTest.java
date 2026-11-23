/**
 * CG-DOC: Unit or integration tests for Certificate Validation Controller Test in the form service.
 */
package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies certificate HTTP contracts using the mocked service;
 * loads no repositories or persistence infrastructure.
 */
@WebMvcTest(value = CertificateValidationController.class, properties = "jwt.secret=test-only-secret-with-at-least-32-bytes")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
class CertificateValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthSurveyService surveyService;

    @Test
    void shouldReturnPendingSurveys() throws Exception {
        // The controller must correctly return the service list.
        when(surveyService.getPendingSurveys()).thenReturn(List.of(new HealthSurvey()));

        mockMvc.perform(get("/api/v1/certificates/pending"))
                .andExpect(status().isOk());

        verify(surveyService).getPendingSurveys();
    }

    @Test
    void shouldApproveCertificate() throws Exception {
        // Sends valid parameters and checks they are mapped to the service.
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/certificates/{id}/validate", surveyId)
                        .param("status", "APPROVED")
                        .param("adminId", adminId.toString()))
                .andExpect(status().isOk());

        verify(surveyService).validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);
    }

    @Test
    void shouldRejectCertificate() throws Exception {
        // The rejection keeps the same HTTP contract with another valid status.
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/certificates/{id}/validate", surveyId)
                        .param("status", "REJECTED")
                        .param("adminId", adminId.toString()))
                .andExpect(status().isOk());

        verify(surveyService).validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);
    }

    @Test
    void shouldRejectInvalidStatus() throws Exception {
        // An unknown enum must be rejected before invoking the service.
        mockMvc.perform(post("/api/v1/certificates/{id}/validate", UUID.randomUUID())
                        .param("status", "INVALID")
                        .param("adminId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(surveyService);
    }

    @Test
    void shouldRejectInvalidSurveyId() throws Exception {
        // A non-UUID identifier must not reach the service layer.
        mockMvc.perform(post("/api/v1/certificates/not-a-uuid/validate")
                        .param("status", "APPROVED")
                        .param("adminId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(surveyService);
    }

    @Test
    void shouldRejectMissingAdminId() throws Exception {
        // The missing required parameter must produce a 400 response.
        mockMvc.perform(post("/api/v1/certificates/{id}/validate", UUID.randomUUID())
                        .param("status", "APPROVED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(surveyService);
    }
}
