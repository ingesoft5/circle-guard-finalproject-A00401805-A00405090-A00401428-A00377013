/**
 * CG-DOC: Unit or integration tests for Health Survey Service Test in the form service.
 */
package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests survey logic with mocked repository, mapper, and Kafka.
 * This verifies states and events without external infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class HealthSurveyServiceTest {

    @Mock
    private HealthSurveyRepository repository;

    @Mock
    private QuestionnaireService questionnaireService;

    @Mock
    private SymptomMapper symptomMapper;

    @Mock
    private FormEventPublisher eventPublisher;

    @InjectMocks
    private HealthSurveyService service;

    @Test
    void shouldInferLegacyFieldsAndPublishSurveyEvent() {
        // An active questionnaire with symptoms must fill the inherited fields.
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = survey(anonymousId);
        Questionnaire questionnaire = Questionnaire.builder().build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(repository.save(survey)).thenReturn(survey);

        // The survey is saved and the event Kafka would consume is published.
        assertSame(survey, service.submitSurvey(survey));

        assertTrue(survey.getHasFever());
        assertTrue(survey.getHasCough());
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq("survey.submitted"), eq(anonymousId.toString()), eventCaptor.capture());
        Map<?, ?> event = eventCaptor.getValue();
        assertEquals(anonymousId, event.get("anonymousId"));
        assertEquals(true, event.get("hasSymptoms"));
        assertTrue(event.get("timestamp") instanceof Number);
    }

    @Test
    void shouldPreserveProvidedLegacyFields() {
        // Values sent by the client must not be overwritten.
        HealthSurvey survey = survey(UUID.randomUUID());
        survey.setHasFever(false);
        survey.setHasCough(true);
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertFalse(survey.getHasFever());
        assertTrue(survey.getHasCough());
        verifyNoInteractions(symptomMapper);
    }

    @Test
    void shouldUseFalseSymptomsWhenThereIsNoActiveQuestionnaire() {
        // Without an active questionnaire, the mapper is not queried and symptoms stay false.
        HealthSurvey survey = survey(UUID.randomUUID());
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertFalse(survey.getHasFever());
        assertFalse(survey.getHasCough());
        verifyNoInteractions(symptomMapper);
        verify(eventPublisher).publish(eq("survey.submitted"), anyString(), any());
    }

    @Test
    void shouldMarkSurveyWithAttachmentAsPending() {
        // The presence of an attachment starts validation in a pending state.
        HealthSurvey survey = survey(UUID.randomUUID());
        survey.setAttachmentPath("certificate.pdf");
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertEquals(ValidationStatus.PENDING, survey.getValidationStatus());
    }

    @Test
    void shouldLeaveValidationStatusUnsetWithoutAttachment() {
        // Without an attachment, the service does not assign a validation status automatically.
        HealthSurvey survey = survey(UUID.randomUUID());
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertNull(survey.getValidationStatus());
    }

    @Test
    void shouldPublishApprovedCertificateEvent() {
        // An approval must save the decision and emit the corresponding event.
        UUID surveyId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HealthSurvey survey = survey(anonymousId);
        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        assertEquals(ValidationStatus.APPROVED, survey.getValidationStatus());
        assertEquals(adminId, survey.getValidatedBy());
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq("certificate.validated"), eq(anonymousId.toString()), eventCaptor.capture());
        Map<?, ?> event = eventCaptor.getValue();
        assertEquals(anonymousId, event.get("anonymousId"));
        assertEquals("APPROVED", event.get("status"));
        assertEquals(adminId, event.get("adminId"));
        assertTrue(event.get("timestamp") instanceof Number);
    }

    @Test
    void shouldSaveRejectedCertificateWithoutPublishingEvent() {
        // A rejection is persisted but does not need to notify access restoration.
        UUID surveyId = UUID.randomUUID();
        HealthSurvey survey = survey(UUID.randomUUID());
        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));

        service.validateSurvey(surveyId, ValidationStatus.REJECTED, UUID.randomUUID());

        verify(repository).save(survey);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldFailValidationWhenSurveyDoesNotExist() {
        // Validation of a missing survey must stop before saving.
        UUID surveyId = UUID.randomUUID();
        when(repository.findById(surveyId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.validateSurvey(surveyId, ValidationStatus.APPROVED, UUID.randomUUID()));

        verify(repository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldPropagateRepositoryFailureWithoutPublishingEvent() {
        // If persistence fails, no survey-sent event should be emitted either.
        HealthSurvey survey = survey(UUID.randomUUID());
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.submitSurvey(survey));

        verifyNoInteractions(eventPublisher);
    }

    private HealthSurvey survey(UUID anonymousId) {
        return HealthSurvey.builder()
                .anonymousId(anonymousId)
                .build();
    }
}
