/** CG-DOC: Tests for Notification Kafka Trace Integration. */
package com.circleguard.notification.observability;

import com.circleguard.notification.service.ExposureNotificationListener;
import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/** Real Kafka consumer proves that Notification extracts the parent trace from Promotion's header. */
@SpringBootTest(classes = NotificationKafkaTraceIntegrationTest.App.class, properties = {
        "management.tracing.enabled=true",
        "management.tracing.sampling.probability=1.0",
        "spring.kafka.listener.observation-enabled=true",
        "spring.kafka.consumer.group-id=notification-hu6-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@AutoConfigureMetrics
class NotificationKafkaTraceIntegrationTest {
    @Container static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", KAFKA::getBootstrapServers);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({KafkaAutoConfiguration.class, ExposureNotificationListener.class})
    static class App {
        @Bean NotificationDispatcher dispatcher() { return Mockito.mock(NotificationDispatcher.class); }
        @Bean LmsService lmsService() { return Mockito.mock(LmsService.class); }
        @Bean SpanExporter spanExporter() { return InMemorySpanExporter.create(); }
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired Tracer tracer;
    @Autowired Propagator propagator;

    @Test
    void statusEventRetainsParentTraceAndExistingPayloadContract() throws Exception {
        String traceId = "0123456789abcdef0123456789abcdef";
        var observedTrace = new AtomicReference<String>();
        doAnswer(invocation -> {
            assertNotNull(tracer.currentSpan());
            observedTrace.set(tracer.currentSpan().context().traceId());
            return null;
        }).when(dispatcher).dispatch("private-user", "SUSPECT");

        var record = new ProducerRecord<String, String>("promotion.status.changed", "private-user",
                "{\"anonymousId\":\"private-user\",\"status\":\"SUSPECT\"}");
        record.headers().add("traceparent",
                ("00-" + traceId + "-0123456789abcdef-01").getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
        assertEquals("00-" + traceId + "-0123456789abcdef-01",
                new String(record.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8));
        var extracted = propagator.extract(record.headers(), (headers, name) -> {
            var value = headers.lastHeader(name);
            return value == null ? null : new String(value.value(), StandardCharsets.UTF_8);
        }).start();
        try { assertEquals(traceId, extracted.context().traceId()); }
        finally { extracted.end(); }
        try (var inspector = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "group.id", "notification-hu6-inspector",
                "auto.offset.reset", "earliest"), new StringDeserializer(), new StringDeserializer())) {
            inspector.subscribe(List.of("promotion.status.changed"));
            var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            boolean found = false;
            while (!found && System.nanoTime() < deadline) {
                for (var received : inspector.poll(Duration.ofSeconds(2))) {
                    var receivedHeader = received.headers().lastHeader("traceparent");
                    assertNotNull(receivedHeader);
                    assertEquals("00-" + traceId + "-0123456789abcdef-01",
                            new String(receivedHeader.value(), StandardCharsets.UTF_8));
                    found = true;
                }
            }
            assertEquals(true, found);
        }

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            verify(dispatcher).dispatch("private-user", "SUSPECT");
            assertEquals(traceId, observedTrace.get());
        });
    }
}
