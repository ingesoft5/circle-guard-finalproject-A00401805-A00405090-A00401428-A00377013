/** CG-DOC: Tests for Kafka Promotion Integration. */
package com.circleguard.promotion.listener;

import com.circleguard.promotion.service.HealthStatusService;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end Kafka contract for promotion-service: a real broker receives the
 * event published by form-service and SurveyListener forwards it to the status
 * cascade. Runs wherever Docker is available (CI); skipped otherwise.
 */
@SpringBootTest(
        classes = KafkaPromotionIntegrationTest.App.class,
        properties = {
                "spring.kafka.consumer.group-id=promotion-service-group",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
                "spring.kafka.consumer.properties.spring.json.value.default.type=java.util.Map",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "spring.kafka.listener.observation-enabled=true",
                "management.tracing.enabled=true",
                "management.tracing.sampling.probability=1.0"
        })
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@AutoConfigureMetrics
class KafkaPromotionIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", KAFKA::getBootstrapServers);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration",
            "org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"
    })
    @EnableConfigurationProperties(KafkaProperties.class)
    @Import({KafkaAutoConfiguration.class, SurveyListener.class})
    static class App {
        @Bean
        HealthStatusService healthStatusService() {
            return Mockito.mock(HealthStatusService.class);
        }
        @Bean SpanExporter spanExporter() { return InMemorySpanExporter.create(); }
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private HealthStatusService healthStatusService;

    @Autowired
    private Tracer tracer;

    @Test
    void surveyWithSymptoms_promotesUserToSuspect() {
        kafkaTemplate.send("survey.submitted", "user-1",
                Map.of("anonymousId", "user-1", "hasSymptoms", true));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                verify(healthStatusService).updateStatus("user-1", "SUSPECT"));
    }

    @Test
    void surveyWithoutSymptoms_isIgnored() {
        kafkaTemplate.send("survey.submitted", "user-2",
                Map.of("anonymousId", "user-2", "hasSymptoms", false));

        // during() keeps asserting for the whole window: pollDelay() alone evaluates once,
        // so a message arriving after the delay would still pass.
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                verify(healthStatusService, never()).updateStatus("user-2", "SUSPECT"));
    }

    @Test
    void approvedCertificate_restoresUserToActive() {
        kafkaTemplate.send("certificate.validated", "user-3",
                Map.of("anonymousId", "user-3", "status", "APPROVED"));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                verify(healthStatusService).updateStatus("user-3", "ACTIVE"));
    }

    @Test
    void surveyListenerExtractsFormTraceparentFromRealKafkaRecord() throws Exception {
        String traceId = "abcdef0123456789abcdef0123456789";
        var observedTrace = new AtomicReference<String>();
        doAnswer(invocation -> {
            assertNotNull(tracer.currentSpan());
            observedTrace.set(tracer.currentSpan().context().traceId());
            return null;
        }).when(healthStatusService).updateStatus("trace-user", "SUSPECT");

        var record = new ProducerRecord<String, Object>("survey.submitted", "trace-user",
                Map.of("anonymousId", "trace-user", "hasSymptoms", true));
        record.headers().add("traceparent",
                ("00-" + traceId + "-0123456789abcdef-01").getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            verify(healthStatusService).updateStatus("trace-user", "SUSPECT");
            assertEquals(traceId, observedTrace.get());
        });
    }
}
