/**
 * CG-DOC: Unit or integration tests for Form Promotion Kafka Integration Test in the form service.
 */
package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.repository.FormOutboxRepository;
import com.circleguard.form.model.FormOutboxEvent;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.FormEventPublisher;
import com.circleguard.form.service.FormOutboxDispatcher;
import com.circleguard.form.observability.TracingFixture;
import com.circleguard.form.observability.FormObservabilityConfiguration;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import java.nio.charset.StandardCharsets;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that Form publishes a survey with symptoms and Promotion processes it
 * after receiving it from a temporary Kafka broker.
 */
@Testcontainers(disabledWithoutDocker = true)
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
@Tag("integration")
class FormPromotionKafkaIntegrationTest {

    private static final String TOPIC = "survey.submitted";
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(20);

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            // The broker with Zookeeper exceeded the 60 s wait strategy timeout in CI.
            // KRaft + bounded heap reduce startup time and memory usage.
            // Wait for the startup script copy to finish: under E/S pressure the
            // default command can hit "Text file busy" and exit 126.
            .withCommand("sh", "-c",
                    "while [ ! -f /tmp/testcontainers_start.sh ]; do sleep 0.1; done; sleep 2; /tmp/testcontainers_start.sh")
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx512M -Xms256M")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", kafka::getBootstrapServers);
    }

    @Autowired
    private HealthSurveyService healthSurveyService;
    @Autowired
    private FormOutboxDispatcher dispatcher;

    @Autowired
    private TracingFixture tracing;

    @MockitoBean
    private HealthSurveyRepository repository;
    @MockitoBean
    private FormOutboxRepository outbox;

    @MockitoBean
    private QuestionnaireService questionnaireService;

    @MockitoBean
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
        AtomicReference<FormOutboxEvent> pending = new AtomicReference<>();
        when(outbox.save(any(FormOutboxEvent.class))).thenAnswer(invocation -> {
            FormOutboxEvent row = invocation.getArgument(0);
            pending.set(row);
            return row;
        });
        when(outbox.lockNextDue()).thenAnswer(invocation -> Optional.ofNullable(pending.get()));

        var parent = tracing.tracer.nextSpan().name("survey-request").start();
        try (var scope = tracing.tracer.withSpan(parent)) {
            healthSurveyService.submitSurvey(survey);
        } finally { parent.end(); }
        assertTrue(dispatcher.dispatchOne());

        ConsumerRecord<String, String> record = receiveSurveyEvent();
        assertEquals(TOPIC, record.topic());
        var header = record.headers().lastHeader("traceparent");
        assertTrue(header != null, "Kafka must transport W3C trace context");
        String traceparent = new String(header.value(), StandardCharsets.UTF_8);
        assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
        assertEquals(parent.context().traceId(), traceparent.split("-")[1]);
        assertEquals(anonymousId.toString(), record.key());

        JsonNode event = new ObjectMapper().readTree(record.value());
        assertEquals(anonymousId.toString(), event.path("anonymousId").asText());
        assertTrue(event.path("hasSymptoms").isBoolean());
        assertTrue(event.path("hasSymptoms").booleanValue());
        assertTrue(event.path("timestamp").isIntegralNumber());
        assertTrue(event.path("timestamp").asLong() > 0);

        // The listener runs via Kafka; the wait accounts for its asynchronous processing.
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                verify(healthStatusService).updateStatus(anonymousId.toString(), "SUSPECT"));
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
    @Import({HealthSurveyService.class, FormEventPublisher.class, FormOutboxDispatcher.class, SymptomMapper.class, SurveyListener.class,
            FormObservabilityConfiguration.class})
    static class KafkaTestApplication {
        @Bean(destroyMethod = "close") TracingFixture tracing() { return new TracingFixture(); }
        @Bean ObservationRegistry observationRegistry(TracingFixture fixture) { return fixture.observations; }
        @Bean Tracer tracer(TracingFixture fixture) { return fixture.tracer; }
        @Bean Propagator propagator(TracingFixture fixture) {
            return new OtelPropagator(fixture.sdk.getPropagators(), fixture.sdk.getTracer("form-test"));
        }
        @Bean MeterRegistry meters() { return new SimpleMeterRegistry(); }

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
