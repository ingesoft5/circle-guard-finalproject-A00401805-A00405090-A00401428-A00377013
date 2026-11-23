/**
 * CG-DOC: Unit or integration tests for Health Survey Controller Test in the form service.
 */
package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.service.HealthSurveyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests only the web layer of HealthSurveyController. The service is
 * replaced by a mock to avoid database and Kafka access.
 */
@WebMvcTest(value = HealthSurveyController.class, properties = "jwt.secret=test-only-secret-with-at-least-32-bytes")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
class HealthSurveyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthSurveyService surveyService;

    @Test
    void shouldSubmitSurveySuccessfully() throws Exception {
        // Prepares the survey the mocked service will return to the controller.
        UUID surveyId = UUID.randomUUID();
        HealthSurvey surveyResponse = new HealthSurvey();
        surveyResponse.setId(surveyId);
        surveyResponse.setAnonymousId(UUID.randomUUID());

        Mockito.when(surveyService.submitSurvey(Mockito.any(HealthSurvey.class))).thenReturn(surveyResponse);

        // Sends the JSON to the endpoint and checks that the response is successful
        // and contains the identifier of the created survey.
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
