package com.circleguard.promotion.listener;

import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
        })
@Testcontainers(disabledWithoutDocker = true)
class KafkaPromotionIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
    })
    @EnableConfigurationProperties(KafkaProperties.class)
    @Import({KafkaAutoConfiguration.class, SurveyListener.class})
    static class App {
        @Bean
        HealthStatusService healthStatusService() {
            return Mockito.mock(HealthStatusService.class);
        }
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private HealthStatusService healthStatusService;

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
}
