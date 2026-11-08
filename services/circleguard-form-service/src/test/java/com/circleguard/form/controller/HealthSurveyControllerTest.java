package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba únicamente la capa web de HealthSurveyController. El servicio se
 * reemplaza por un mock para evitar acceso a base de datos y Kafka.
 */
@WebMvcTest(HealthSurveyController.class)
class HealthSurveyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthSurveyService surveyService;

    @Test
    void shouldSubmitSurveySuccessfully() throws Exception {
        // Prepara la encuesta que el servicio simulado devolverá al controlador.
        UUID surveyId = UUID.randomUUID();
        HealthSurvey surveyResponse = new HealthSurvey();
        surveyResponse.setId(surveyId);
        surveyResponse.setAnonymousId(UUID.randomUUID());

        Mockito.when(surveyService.submitSurvey(Mockito.any(HealthSurvey.class))).thenReturn(surveyResponse);

        // Envía el JSON al endpoint y comprueba que la respuesta sea exitosa
        // y contenga el identificador de la encuesta creada.
        mockMvc.perform(post("/api/v1/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"550e8400-e29b-41d4-a716-446655440000\", \"responses\": {\"q1\": \"YES\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        ArgumentCaptor<HealthSurvey> surveyCaptor = ArgumentCaptor.forClass(HealthSurvey.class);
        Mockito.verify(surveyService).submitSurvey(surveyCaptor.capture());
        HealthSurvey submitted = surveyCaptor.getValue();
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), submitted.getAnonymousId());
        assertEquals(Map.of("q1", "YES"), submitted.getResponses());
    }
}
