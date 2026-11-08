/**
 * CG-DOC: Unit or integration tests for Form Promotion Kafka Integration Test in the form service.
 */
package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import com.circleguard.form.service.SymptomMapper;
import com.circleguard.promotion.listener.SurveyListener;
import com.circleguard.promotion.service.HealthStatusService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica que Form publique una encuesta con síntomas y que Promotion la procese
 * después de recibirla desde un broker Kafka temporal.
 */
@Testcontainers
@SpringBootTest(
        classes = FormPromotionKafkaIntegrationTest.KafkaTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.consumer.group-id=promotion-service-group",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*"
        }
)
class FormPromotionKafkaIntegrationTest {

    private static final String TOPIC = "survey.submitted";
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(20);

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private HealthSurveyService healthSurveyService;

    @MockBean
    private HealthSurveyRepository repository;

    @MockBean
    private QuestionnaireService questionnaireService;

    @MockBean
    private HealthStatusService healthStatusService;

    @Test
    void submitsSymptomaticSurveyAndRequestsSuspectStatus() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        Question feverQuestion = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(feverQuestion))
                .build();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .responses(Map.of(questionId.toString(), "YES"))
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(repository.save(any(HealthSurvey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        healthSurveyService.submitSurvey(survey);

        ConsumerRecord<String, String> record = receiveSurveyEvent();
        assertEquals(TOPIC, record.topic());
        assertEquals(anonymousId.toString(), record.key());

        JsonNode event = new ObjectMapper().readTree(record.value());
        assertEquals(anonymousId.toString(), event.path("anonymousId").asText());
        assertTrue(event.path("hasSymptoms").isBoolean());
        assertTrue(event.path("hasSymptoms").booleanValue());
        assertTrue(event.path("timestamp").isIntegralNumber());
        assertTrue(event.path("timestamp").asLong() > 0);

        // El listener se ejecuta mediante Kafka; la espera contempla su procesamiento asíncrono.
        verify(healthStatusService, timeout(20_000))
                .updateStatus(anonymousId.toString(), "SUSPECT");
    }

    private ConsumerRecord<String, String> receiveSurveyEvent() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "survey-verifier-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + RECEIVE_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    return record;
                }
            }
        }

        throw new AssertionError("No event was received from topic " + TOPIC);
    }

    @SpringBootConfiguration
    @EnableKafka
    @ImportAutoConfiguration(KafkaAutoConfiguration.class)
    @Import({HealthSurveyService.class, SymptomMapper.class, SurveyListener.class})
    static class KafkaTestApplication {

        @Bean
        NewTopic surveySubmittedTopic() {
            return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic certificateValidatedTopic() {
            return TopicBuilder.name("certificate.validated").partitions(1).replicas(1).build();
        }
    }
}
