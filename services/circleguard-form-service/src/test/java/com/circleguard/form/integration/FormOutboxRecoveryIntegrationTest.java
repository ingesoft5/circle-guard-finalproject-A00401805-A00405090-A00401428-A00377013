package com.circleguard.form.integration;

import com.circleguard.form.FormApplication;
import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.repository.FormOutboxRepository;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.service.FormOutboxDispatcher;
import com.circleguard.form.service.FormOutboxScheduler;
import com.circleguard.form.service.HealthSurveyService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

@Tag("integration")
@Testcontainers
@Import(FormOutboxRecoveryIntegrationTest.SpyProducerConfig.class)
@SpringBootTest(classes = FormApplication.class, properties = {
        "spring.kafka.producer.properties.max.block.ms=1000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=2000",
        "form.outbox.ack-timeout-ms=1200",
        "form.outbox.retry-attempts=2",
        "form.outbox.retry-wait-ms=50",
        "jwt.secret=test-only-secret-with-at-least-32-bytes"
})
class FormOutboxRecoveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Container static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx512M -Xms256M")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired HealthSurveyService surveys;
    @Autowired HealthSurveyRepository surveyRows;
    @Autowired FormOutboxRepository outbox;
    @Autowired FormOutboxDispatcher dispatcher;
    @MockBean FormOutboxScheduler scheduler;
    @Autowired @Qualifier("kafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate;

    @TestConfiguration
    static class SpyProducerConfig {
        @Bean
        static BeanPostProcessor spyKafkaTemplate() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return "kafkaTemplate".equals(beanName) ? spy(bean) : bean;
                }
            };
        }
    }

    @Test
    void retainsCommittedSurveyAndEventDuringOutageThenPublishesAfterRecovery() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        AtomicInteger failures = new AtomicInteger();
        doAnswer(invocation -> {
            failures.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("Kafka temporarily unavailable"));
        }).when(kafkaTemplate).send(any(ProducerRecord.class));
        try {
            HealthSurvey saved = surveys.submitSurvey(HealthSurvey.builder().anonymousId(anonymousId).build());
            assertTrue(surveyRows.existsById(saved.getId()));
            assertEquals(1, outbox.count());
            assertTrue(dispatcher.dispatchOne());
            assertEquals(1, outbox.count(), "A failed acknowledgement must retain the row");
            assertEquals(1, outbox.findAll().getFirst().getAttempts());
            assertEquals(2, failures.get(), "Retry must stop after the configured limit");
        } finally {
            reset(kafkaTemplate);
        }

        Thread.sleep(1500); // The failed row is deferred for one second.
        assertTrue(dispatcher.dispatchOne());
        assertEquals(0, outbox.count());

        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-recovery-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of("survey.submitted"));
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofSeconds(1))) {
                    assertEquals(anonymousId.toString(), record.key());
                    assertTrue(record.value().contains(anonymousId.toString()));
                    assertNotNull(record.headers().lastHeader("form-event-id"));
                    return;
                }
            }
        }
        fail("Recovered event was not received from Kafka");
    }
}
