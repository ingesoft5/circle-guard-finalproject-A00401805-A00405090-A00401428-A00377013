/**
 * CG-DOC: Unit or integration tests for Questionnaire Service Test in the form service.
 */
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
 * Tests questionnaire logic with a mocked repository, without starting
 * neither Spring nor database access.
 */
@ExtendWith(MockitoExtension.class)
class QuestionnaireServiceTest {

    @Mock
    private QuestionnaireRepository repository;

    @InjectMocks
    private QuestionnaireService service;

    @Test
    void shouldLinkQuestionsBeforeSavingQuestionnaire() {
        // Prepares a questionnaire with questions that initially have no parent.
        Question first = question();
        Question second = question();
        Questionnaire questionnaire = Questionnaire.builder()
                .title("Daily Health Check")
                .questions(List.of(first, second))
                .build();
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        // Saves the questionnaire and checks the association before persisting it.
        Questionnaire saved = service.saveQuestionnaire(questionnaire);

        assertSame(questionnaire, saved);
        assertSame(questionnaire, first.getQuestionnaire());
        assertSame(questionnaire, second.getQuestionnaire());
        verify(repository).save(questionnaire);
    }

    @Test
    void shouldSaveQuestionnaireWithoutQuestions() {
        // A questionnaire without questions must be delegated to the repository without errors.
        Questionnaire questionnaire = Questionnaire.builder().title("Empty Survey").build();
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        assertSame(questionnaire, service.saveQuestionnaire(questionnaire));

        verify(repository).save(questionnaire);
    }

    @Test
    void shouldDeactivateActiveQuestionnairesAndActivateTarget() {
        // Simulates one active questionnaire, one inactive, and the target to activate.
        UUID targetId = UUID.randomUUID();
        Questionnaire active = Questionnaire.builder().isActive(true).build();
        Questionnaire inactive = Questionnaire.builder().isActive(false).build();
        Questionnaire target = Questionnaire.builder().id(targetId).isActive(false).build();
        when(repository.findAll()).thenReturn(List.of(active, inactive));
        when(repository.findById(targetId)).thenReturn(java.util.Optional.of(target));

        // Activation must change only the corresponding states.
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
