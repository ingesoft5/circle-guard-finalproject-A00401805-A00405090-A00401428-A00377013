/**
 * CG-DOC: Unit or integration tests for Questionnaire Controller Test in the form service.
 */
package com.circleguard.form.controller;

import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.QuestionnaireService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies QuestionnaireController HTTP responses with MockMvc. The
 * business logic is isolated with a QuestionnaireService mock.
 */
@WebMvcTest(value = QuestionnaireController.class, properties = "jwt.secret=test-only-secret-with-at-least-32-bytes")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
class QuestionnaireControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionnaireService questionnaireService;

    @Test
    void shouldReturnActiveQuestionnaire() throws Exception {
        // Configures the service to simulate an active questionnaire.
        UUID id = UUID.randomUUID();
        Questionnaire q = Questionnaire.builder()
                .id(id)
                .title("Daily Health Check")
                .isActive(true)
                .version(1)
                .build();

        Mockito.when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(q));

        // Verifies that the endpoint transforms the result into a 200 response.
        mockMvc.perform(get("/api/v1/questionnaires/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Daily Health Check"));
    }

    @Test
    void shouldReturnNotFoundWhenThereIsNoActiveQuestionnaire() throws Exception {
        Mockito.when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/questionnaires/active"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateQuestionnaire() throws Exception {
        // Represents the questionnaire the service would return after saving it.
        UUID id = UUID.randomUUID();
        Questionnaire q = Questionnaire.builder()
                .id(id)
                .title("New Survey")
                .version(1)
                .build();

        Mockito.when(questionnaireService.saveQuestionnaire(Mockito.any(Questionnaire.class))).thenReturn(q);

        // Sends the creation data and validates the JSON response content.
        mockMvc.perform(post("/api/v1/questionnaires")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"New Survey\", \"version\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Survey"));
    }

    @Test
    void shouldActivateQuestionnaire() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/questionnaires/{id}/activate", id))
                .andExpect(status().isOk());

        Mockito.verify(questionnaireService).activateQuestionnaire(id);
    }
}
