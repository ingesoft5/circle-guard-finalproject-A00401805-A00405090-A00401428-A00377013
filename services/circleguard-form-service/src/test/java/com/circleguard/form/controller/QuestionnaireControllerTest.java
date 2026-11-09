/**
 * CG-DOC: Unit or integration tests for Questionnaire Controller Test in the form service.
 */
package com.circleguard.form.controller;

import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.QuestionnaireService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica las respuestas HTTP de QuestionnaireController con MockMvc. La
 * lógica de negocio se aísla mediante un mock de QuestionnaireService.
 */
@WebMvcTest(QuestionnaireController.class)
class QuestionnaireControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionnaireService questionnaireService;

    @Test
    void shouldReturnActiveQuestionnaire() throws Exception {
        // Configura el servicio para simular que existe un cuestionario activo.
        UUID id = UUID.randomUUID();
        Questionnaire q = Questionnaire.builder()
                .id(id)
                .title("Daily Health Check")
                .isActive(true)
                .version(1)
                .build();

        Mockito.when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(q));

        // Verifica que el endpoint transforme el resultado en una respuesta 200.
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
        // Representa el cuestionario que devolvería el servicio después de guardarlo.
        UUID id = UUID.randomUUID();
        Questionnaire q = Questionnaire.builder()
                .id(id)
                .title("New Survey")
                .version(1)
                .build();

        Mockito.when(questionnaireService.saveQuestionnaire(Mockito.any(Questionnaire.class))).thenReturn(q);

        // Envía los datos de creación y valida el contenido de la respuesta JSON.
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
