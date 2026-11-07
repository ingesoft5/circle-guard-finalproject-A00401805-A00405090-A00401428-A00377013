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
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Prueba la lógica de encuestas con repositorio, mapper y Kafka simulados.
 * Así se verifican estados y eventos sin usar infraestructura externa.
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
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private HealthSurveyService service;

    @Test
    void shouldInferLegacyFieldsAndPublishSurveyEvent() {
        // Un cuestionario activo con síntomas debe completar los campos heredados.
        UUID anonymousId = UUID.randomUUID();
        HealthSurvey survey = survey(anonymousId);
        Questionnaire questionnaire = Questionnaire.builder().build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(repository.save(survey)).thenReturn(survey);

        // La encuesta se guarda y se publica el evento que consumiría Kafka.
        assertSame(survey, service.submitSurvey(survey));

        assertTrue(survey.getHasFever());
        assertTrue(survey.getHasCough());
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("survey.submitted"), eq(anonymousId.toString()), eventCaptor.capture());
        Map<?, ?> event = eventCaptor.getValue();
        assertEquals(anonymousId, event.get("anonymousId"));
        assertEquals(true, event.get("hasSymptoms"));
        assertTrue(event.get("timestamp") instanceof Number);
    }

    @Test
    void shouldPreserveProvidedLegacyFields() {
        // Los valores enviados por el cliente no deben ser reemplazados.
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
        // Sin cuestionario activo no se consulta el mapper y los síntomas quedan en false.
        HealthSurvey survey = survey(UUID.randomUUID());
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertFalse(survey.getHasFever());
        assertFalse(survey.getHasCough());
        verifyNoInteractions(symptomMapper);
        verify(kafkaTemplate).send(eq("survey.submitted"), anyString(), any());
    }

    @Test
    void shouldMarkSurveyWithAttachmentAsPending() {
        // La presencia de un adjunto inicia la validación en estado pendiente.
        HealthSurvey survey = survey(UUID.randomUUID());
        survey.setAttachmentPath("certificate.pdf");
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertEquals(ValidationStatus.PENDING, survey.getValidationStatus());
    }

    @Test
    void shouldLeaveValidationStatusUnsetWithoutAttachment() {
        // Sin adjunto, el servicio no asigna un estado de validación automáticamente.
        HealthSurvey survey = survey(UUID.randomUUID());
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(survey);

        service.submitSurvey(survey);

        assertNull(survey.getValidationStatus());
    }

    @Test
    void shouldPublishApprovedCertificateEvent() {
        // Una aprobación debe guardar la decisión y emitir el evento correspondiente.
        UUID surveyId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HealthSurvey survey = survey(anonymousId);
        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        assertEquals(ValidationStatus.APPROVED, survey.getValidationStatus());
        assertEquals(adminId, survey.getValidatedBy());
        ArgumentCaptor<Map> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("certificate.validated"), eq(anonymousId.toString()), eventCaptor.capture());
        Map<?, ?> event = eventCaptor.getValue();
        assertEquals(anonymousId, event.get("anonymousId"));
        assertEquals("APPROVED", event.get("status"));
        assertEquals(adminId, event.get("adminId"));
        assertTrue(event.get("timestamp") instanceof Number);
    }

    @Test
    void shouldSaveRejectedCertificateWithoutPublishingEvent() {
        // Un rechazo se persiste, pero no necesita notificar la restauración de acceso.
        UUID surveyId = UUID.randomUUID();
        HealthSurvey survey = survey(UUID.randomUUID());
        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));

        service.validateSurvey(surveyId, ValidationStatus.REJECTED, UUID.randomUUID());

        verify(repository).save(survey);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void shouldFailValidationWhenSurveyDoesNotExist() {
        // La validación de una encuesta inexistente debe detenerse antes de guardar.
        UUID surveyId = UUID.randomUUID();
        when(repository.findById(surveyId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.validateSurvey(surveyId, ValidationStatus.APPROVED, UUID.randomUUID()));

        verify(repository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void shouldPropagateRepositoryFailureWithoutPublishingEvent() {
        // Si falla la persistencia, tampoco debe emitirse un evento de encuesta enviada.
        HealthSurvey survey = survey(UUID.randomUUID());
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.submitSurvey(survey));

        verifyNoInteractions(kafkaTemplate);
    }

    private HealthSurvey survey(UUID anonymousId) {
        return HealthSurvey.builder()
                .anonymousId(anonymousId)
                .build();
    }
}
