package com.circleguard.form.service;

import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.QuestionnaireRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Prueba la lógica de cuestionarios con un repositorio simulado, sin iniciar
 * Spring ni acceder a la base de datos.
 */
@ExtendWith(MockitoExtension.class)
class QuestionnaireServiceTest {

    @Mock
    private QuestionnaireRepository repository;

    @InjectMocks
    private QuestionnaireService service;

    @Test
    void shouldLinkQuestionsBeforeSavingQuestionnaire() {
        // Prepara un cuestionario con preguntas que inicialmente no tienen padre.
        Question first = question();
        Question second = question();
        Questionnaire questionnaire = Questionnaire.builder()
                .title("Daily Health Check")
                .questions(List.of(first, second))
                .build();
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        // Guarda el cuestionario y comprueba la asociación antes de persistirlo.
        Questionnaire saved = service.saveQuestionnaire(questionnaire);

        assertSame(questionnaire, saved);
        assertSame(questionnaire, first.getQuestionnaire());
        assertSame(questionnaire, second.getQuestionnaire());
        verify(repository).save(questionnaire);
    }

    @Test
    void shouldSaveQuestionnaireWithoutQuestions() {
        // Un cuestionario sin preguntas debe delegarse al repositorio sin errores.
        Questionnaire questionnaire = Questionnaire.builder().title("Empty Survey").build();
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        assertSame(questionnaire, service.saveQuestionnaire(questionnaire));

        verify(repository).save(questionnaire);
    }

    @Test
    void shouldDeactivateActiveQuestionnairesAndActivateTarget() {
        // Simula un cuestionario activo, otro inactivo y el objetivo a activar.
        UUID targetId = UUID.randomUUID();
        Questionnaire active = Questionnaire.builder().isActive(true).build();
        Questionnaire inactive = Questionnaire.builder().isActive(false).build();
        Questionnaire target = Questionnaire.builder().id(targetId).isActive(false).build();
        when(repository.findAll()).thenReturn(List.of(active, inactive));
        when(repository.findById(targetId)).thenReturn(java.util.Optional.of(target));

        // La activación debe cambiar solo los estados que corresponden.
        service.activateQuestionnaire(targetId);

        verify(repository).save(same(active));
        verify(repository).save(same(target));
        verify(repository, never()).save(same(inactive));
        assertFalse(active.getIsActive());
        assertTrue(target.getIsActive());
    }

    private Question question() {
        return Question.builder()
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .orderIndex(1)
                .build();
    }
}
