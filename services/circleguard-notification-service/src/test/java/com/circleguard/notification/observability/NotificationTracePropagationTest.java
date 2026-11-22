package com.circleguard.notification.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationTracePropagationTest {
    @Test
    void auditEventCarriesParentTraceWithoutExportingPayload() throws Exception {
        try (var tracing = new TracingFixture(); var context = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("observationRegistry", tracing.observations);
            var producer = new MockProducer<String, String>(true, new StringSerializer(), new StringSerializer());
            ProducerFactory<String, String> factory = mock(ProducerFactory.class);
            when(factory.createProducer()).thenReturn(producer);
            var template = new KafkaTemplate<>(factory);
            template.setApplicationContext(context);
            var admin = mock(KafkaAdmin.class);
            when(admin.clusterId()).thenReturn("test-cluster");
            template.setKafkaAdmin(admin);
            NotificationTracingConfiguration.notificationKafkaObservation()
                    .postProcessAfterInitialization(template, "kafkaTemplate");
            template.afterSingletonsInstantiated();
            var parent = tracing.tracer.nextSpan().name("status-consumer").start();
            try (var scope = tracing.tracer.withSpan(parent)) {
                template.send("notification.audit", "private-user", "private-health").get();
            } finally { parent.end(); }
            var record = producer.history().getFirst();
            var header = record.headers().lastHeader("traceparent");
            assertNotNull(header);
            assertTrue(new String(header.value(), StandardCharsets.UTF_8)
                    .contains(parent.context().traceId()));
            assertEquals("private-user", record.key());
            assertEquals("private-health", record.value());
            var child = tracing.exported.getFinishedSpanItems().stream()
                    .filter(s -> s.getParentSpanContext().getSpanId().equals(parent.context().spanId()))
                    .findFirst().orElseThrow();
            assertEquals(parent.context().traceId(), child.getTraceId());
            assertFalse(child.getAttributes().toString().contains("private-user"));
            assertFalse(child.getAttributes().toString().contains("private-health"));
            template.destroy();
        }
    }

    @Test
    void exporterDropsUnapprovedAttributesAndExceptionDetails() {
        try (var tracing = new TracingFixture()) {
            var span = tracing.sdk.getTracer("notification-test").spanBuilder("operation").startSpan();
            span.setAttribute("http.method", "POST");
            span.setAttribute("messaging.kafka.message.key", "private-user");
            span.setAttribute("uri", "/notify?token=private-health");
            span.recordException(new IllegalStateException("private-health"));
            span.setStatus(StatusCode.ERROR, "private-health");
            span.end();
            var safe = tracing.exported.getFinishedSpanItems().getFirst();
            assertEquals("POST", safe.getAttributes().get(AttributeKey.stringKey("http.method")));
            assertEquals(1, safe.getAttributes().size());
            assertTrue(safe.getEvents().isEmpty());
            assertEquals("", safe.getStatus().getDescription());
        }
    }
}
