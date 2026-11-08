/**
 * CG-DOC: Unit or integration tests for Symptom Mapper Test in the form service.
 */
package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias puras de SymptomMapper: construyen cuestionarios y
 * respuestas en memoria, sin iniciar Spring ni utilizar infraestructura.
 */
class SymptomMapperTest {

    private final SymptomMapper mapper = new SymptomMapper();

    @Test
    void shouldDetectSymptomsFromFever() {
        // La respuesta se vincula con la pregunta mediante el mismo UUID.
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();
        
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();
        
        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "YES"))
                .build();
        
        // Una respuesta afirmativa a una pregunta de fiebre indica síntomas.
        assertTrue(mapper.hasSymptoms(survey, questionnaire));
    }

    @Test
    void shouldNotDetectSymptomsWhenNo() {
        // Mantiene la misma pregunta, pero cambia la respuesta para probar
        // el camino negativo de la lógica.
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();
        
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();
        
        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "NO"))
                .build();
        
        assertFalse(mapper.hasSymptoms(survey, questionnaire));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Do you have a cough?", "DO YOU HAVE TROUBLE BREATHING?"})
    void shouldDetectSymptomsFromCoughOrBreathing(String questionText) {
        UUID questionId = UUID.randomUUID();
        Questionnaire questionnaire = questionnaireWith(questionId, questionText, QuestionType.YES_NO);

        assertTrue(mapper.hasSymptoms(surveyWith(questionId, "yes"), questionnaire));
    }

    @Test
    void shouldIgnoreAffirmativeAnswerToUnrelatedQuestion() {
        UUID questionId = UUID.randomUUID();
        Questionnaire questionnaire = questionnaireWith(questionId, "Do you have a headache?", QuestionType.YES_NO);

        assertFalse(mapper.hasSymptoms(surveyWith(questionId, "YES"), questionnaire));
    }

    @Test
    void shouldReturnFalseForMissingOrEmptyResponsesAndQuestions() {
        HealthSurvey withoutResponses = HealthSurvey.builder().build();
        Questionnaire emptyQuestionnaire = Questionnaire.builder().questions(List.of()).build();

        assertFalse(mapper.hasSymptoms(withoutResponses, emptyQuestionnaire));
        assertFalse(mapper.hasSymptoms(surveyWith(UUID.randomUUID(), "YES"), null));
        assertFalse(mapper.hasSymptoms(surveyWith(UUID.randomUUID(), "YES"),
                Questionnaire.builder().build()));
    }

    @Test
    void shouldIgnoreResponseWithoutMatchingQuestion() {
        UUID questionId = UUID.randomUUID();
        Questionnaire questionnaire = questionnaireWith(questionId, "Do you have a fever?", QuestionType.YES_NO);

        assertFalse(mapper.hasSymptoms(surveyWith(UUID.randomUUID(), "YES"), questionnaire));
    }

    @Test
    void shouldDetectSymptomsWhenAnyMatchingQuestionIsAffirmative() {
        UUID negativeQuestionId = UUID.randomUUID();
        UUID positiveQuestionId = UUID.randomUUID();
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(
                        questionWith(negativeQuestionId, "Do you have a fever?", QuestionType.YES_NO),
                        questionWith(positiveQuestionId, "Do you have a cough?", QuestionType.YES_NO)))
                .build();

        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(negativeQuestionId.toString(), "NO", positiveQuestionId.toString(), "YES"))
                .build();

        assertTrue(mapper.hasSymptoms(survey, questionnaire));
    }

    @Test
    void shouldDetectSymptomsFromNonEmptyChoiceAboutSymptoms() {
        UUID questionId = UUID.randomUUID();
        Questionnaire questionnaire = questionnaireWith(questionId, "Select your symptoms", QuestionType.MULTI_CHOICE);

        assertTrue(mapper.hasSymptoms(surveyWith(questionId, List.of("FEVER")), questionnaire));
    }

    @Test
    void shouldNotDetectSymptomsFromEmptyChoiceAboutSymptoms() {
        UUID questionId = UUID.randomUUID();
        Questionnaire questionnaire = questionnaireWith(questionId, "Select your symptoms", QuestionType.SINGLE_CHOICE);

        assertFalse(mapper.hasSymptoms(surveyWith(questionId, List.of()), questionnaire));
    }

    private Questionnaire questionnaireWith(UUID questionId, String text, QuestionType type) {
        return Questionnaire.builder()
                .questions(List.of(questionWith(questionId, text, type)))
                .build();
    }

    private Question questionWith(UUID questionId, String text, QuestionType type) {
        return Question.builder()
                .id(questionId)
                .text(text)
                .type(type)
                .build();
    }

    private HealthSurvey surveyWith(UUID questionId, Object answer) {
        return HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), answer))
                .build();
    }
}
