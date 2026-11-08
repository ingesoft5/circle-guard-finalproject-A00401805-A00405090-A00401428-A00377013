package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica los contratos HTTP de certificados usando el servicio simulado;
 * no carga repositorios ni infraestructura de persistencia.
 */
@WebMvcTest(CertificateValidationController.class)
class CertificateValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthSurveyService surveyService;

    @Test
    void shouldReturnPendingSurveys() throws Exception {
        // El controlador debe devolver correctamente la lista del servicio.
        when(surveyService.getPendingSurveys()).thenReturn(List.of(new HealthSurvey()));

        mockMvc.perform(get("/api/v1/certificates/pending"))
                .andExpect(status().isOk());

        verify(surveyService).getPendingSurveys();
    }

    @Test
    void shouldApproveCertificate() throws Exception {
        // Envía parámetros válidos y comprueba que se traduzcan al servicio.
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
        // El rechazo conserva el mismo contrato HTTP con otro estado válido.
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
        // Un enum desconocido debe rechazarse antes de invocar el servicio.
        mockMvc.perform(post("/api/v1/certificates/{id}/validate", UUID.randomUUID())
                        .param("status", "INVALID")
                        .param("adminId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(surveyService);
    }

    @Test
    void shouldRejectInvalidSurveyId() throws Exception {
        // Un identificador que no es UUID no debe llegar a la capa de servicio.
        mockMvc.perform(post("/api/v1/certificates/not-a-uuid/validate")
                        .param("status", "APPROVED")
                        .param("adminId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(surveyService);
    }

    @Test
    void shouldRejectMissingAdminId() throws Exception {
        // El parámetro obligatorio ausente debe producir una respuesta 400.
        mockMvc.perform(post("/api/v1/certificates/{id}/validate", UUID.randomUUID())
                        .param("status", "APPROVED"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(surveyService);
    }
}
