/**
 * CG-DOC: Kafka consumption integration test for the notification service.
 * Boots a KRaft broker, publishes the promotion status event that
 * ExposureNotificationListener consumes and asserts the dispatch (and its retry
 * behaviour) through the notification.audit topic.
 */
package com.circleguard.notification.integration;

import com.circleguard.notification.service.AuditLogService;
import com.circleguard.notification.service.EmailServiceImpl;
import com.circleguard.notification.service.ExposureNotificationListener;
import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import com.circleguard.notification.service.PushServiceImpl;
import com.circleguard.notification.service.SmsServiceImpl;
import com.circleguard.notification.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Ciclo completo del canal de exposición: promotion.status.changed -> listener ->
 * dispatcher -> canales -> notification.audit, sobre un broker Kafka temporal.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = NotificationKafkaIntegrationTest.KafkaTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
        }
)
@Tag("integration")
class NotificationKafkaIntegrationTest {

    private static final String STATUS_TOPIC = "promotion.status.changed";
    private static final String AUDIT_TOPIC = "notification.audit";

    /** Cada aserción espera el fin del despacho asíncrono; 60 s cubre un host cargado. */
    private static final Duration AUDIT_TIMEOUT = Duration.ofSeconds(60);

    /** Ventana de silencio para probar el filtro: el evento ACTIVE no debe auditar nada. */
    private static final Duration SILENCE_WINDOW = Duration.ofSeconds(6);

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            // KRaft (imagen Confluent) + heap acotado: el modo Zookeeper superaba el
            // wait strategy en CI. Nunca volver al KafkaContainer con Zookeeper.
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx512M -Xms256M")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private TemplateService templateService;

    @MockBean
    private LmsService lmsService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void dispatchesEveryChannelForAnExposureStatus() throws Exception {
        String anonymousId = "anon-happy-" + UUID.randomUUID();

        publishStatusChange(anonymousId, "PROBABLE");

        Map<String, Object> email = awaitAudit(anonymousId, "EMAIL", "SUCCESS");
        assertThat(email)
                .containsEntry("status", "SUCCESS")
                .containsEntry("channel", "EMAIL")
                .containsEntry("userId", anonymousId);
        assertThat((String) email.get("correlationId")).isNotBlank();
        assertThat((String) email.get("eventId")).isNotBlank();
        assertThat((String) email.get("timestamp")).isNotBlank();

        assertThat(awaitAudit(anonymousId, "SMS", "SUCCESS")).containsEntry("channel", "SMS");
        assertThat(awaitAudit(anonymousId, "PUSH", "SUCCESS")).containsEntry("channel", "PUSH");
    }

    @Test
    void ignoresTheActiveStatusWithoutDispatchingOrAuditing() throws Exception {
        String anonymousId = "anon-active-" + UUID.randomUUID();

        publishStatusChange(anonymousId, "ACTIVE");

        List<Map<String, Object>> audits = drainAudits(SILENCE_WINDOW);
        assertThat(audits)
                .as("an ACTIVE status is not an exposure and must not reach any channel")
                .noneMatch(audit -> anonymousId.equals(audit.get("userId")));
    }

    @Test
    void retriesTheFailingChannelAndAuditsRetryThenFailed() throws Exception {
        String anonymousId = "anon-failing-" + UUID.randomUUID();
        doThrow(new RuntimeException("Mail server down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        publishStatusChange(anonymousId, "SUSPECT");

        // @Retryable: 3 intentos con 2000 ms de backoff -> RETRY por intento fallido
        // y un FAILED desde el @Recover cuando se agota el presupuesto.
        assertThat(awaitAudit(anonymousId, "EMAIL", "RETRY")).containsEntry("status", "RETRY");
        assertThat(awaitAudit(anonymousId, "EMAIL", "FAILED")).containsEntry("status", "FAILED");

        // El resto de canales no comparte el fallo del correo.
        assertThat(awaitAudit(anonymousId, "SMS", "SUCCESS")).containsEntry("status", "SUCCESS");
    }

    private void publishStatusChange(String anonymousId, String status) throws Exception {
        Map<String, Object> event = Map.of("anonymousId", anonymousId, "status", status);
        kafkaTemplate.send(STATUS_TOPIC, anonymousId, event).get(10, TimeUnit.SECONDS);
    }

    private Map<String, Object> awaitAudit(String userId, String channel, String status) {
        long deadline = System.nanoTime() + AUDIT_TIMEOUT.toNanos();
        List<Map<String, Object>> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = auditConsumer()) {
            while (System.nanoTime() < deadline) {
                for (Map<String, Object> audit : pollAudits(consumer, Duration.ofSeconds(1))) {
                    seen.add(audit);
                    if (userId.equals(audit.get("userId"))
                            && channel.equals(audit.get("channel"))
                            && status.equals(audit.get("status"))) {
                        return audit;
                    }
                }
            }
        }
        throw new AssertionError(
                "No " + channel + "/" + status + " audit for " + userId + " within " + AUDIT_TIMEOUT
                        + ". Received: " + seen);
    }

    private List<Map<String, Object>> drainAudits(Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        List<Map<String, Object>> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = auditConsumer()) {
            while (System.nanoTime() < deadline) {
                seen.addAll(pollAudits(consumer, Duration.ofMillis(500)));
            }
        }
        return seen;
    }

    private KafkaConsumer<String, String> auditConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-verifier-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(AUDIT_TOPIC));
        return consumer;
    }

    private List<Map<String, Object>> pollAudits(KafkaConsumer<String, String> consumer, Duration timeout) {
        ConsumerRecords<String, String> records = consumer.poll(timeout);
        List<Map<String, Object>> audits = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            try {
                audits.add(new ObjectMapper().readValue(record.value(), Map.class));
            } catch (Exception e) {
                throw new AssertionError("Unreadable audit payload: " + record.value(), e);
            }
        }
        return audits;
    }

    @SpringBootConfiguration
    @EnableKafka
    @EnableAsync
    @EnableRetry
    @ImportAutoConfiguration({KafkaAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({
            ExposureNotificationListener.class,
            NotificationDispatcher.class,
            EmailServiceImpl.class,
            SmsServiceImpl.class,
            PushServiceImpl.class,
            AuditLogService.class
    })
    static class KafkaTestApplication {

        @Bean
        NewTopic promotionStatusChangedTopic() {
            return TopicBuilder.name(STATUS_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic notificationAuditTopic() {
            return TopicBuilder.name(AUDIT_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
}
