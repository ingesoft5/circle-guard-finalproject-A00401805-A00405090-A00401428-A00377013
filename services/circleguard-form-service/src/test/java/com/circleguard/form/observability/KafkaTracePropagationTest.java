package com.circleguard.form.observability;

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

class KafkaTracePropagationTest {
    @Test
    void realKafkaTemplateInjectsW3cHeadersAndExportsSafeChildSpan() throws Exception {
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
            FormObservabilityConfiguration.formKafkaObservation().postProcessAfterInitialization(template, "kafkaTemplate");
            template.afterSingletonsInstantiated();
            var parent = tracing.tracer.nextSpan().name("request").start();
            try (var scope = tracing.tracer.withSpan(parent)) {
                template.send("survey.submitted", "private-user", "private-health").get();
            } finally { parent.end(); }
            var header = producer.history().getFirst().headers().lastHeader("traceparent");
            assertNotNull(header);
            String value = new String(header.value(), StandardCharsets.UTF_8);
            assertTrue(value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
            assertTrue(value.contains(parent.context().traceId()));
            var child = tracing.exported.getFinishedSpanItems().stream()
                    .filter(s -> s.getParentSpanContext().getSpanId().equals(parent.context().spanId()))
                    .findFirst().orElseThrow();
            assertEquals(parent.context().traceId(), child.getTraceId());
            assertFalse(child.getAttributes().toString().contains("private-user"));
            assertFalse(child.getAttributes().toString().contains("private-health"));
            assertEquals("private-user", producer.history().getFirst().key());
            assertEquals("private-health", producer.history().getFirst().value());
            template.destroy();
        }
    }

    @Test
    void exporterRemovesSensitiveAttributesAndExceptionDetails() {
        try (var tracing = new TracingFixture()) {
            var span = tracing.sdk.getTracer("form-test").spanBuilder("operation").startSpan();
            span.setAttribute("http.method", "POST");
            span.setAttribute("http.url", "https://app/surveys?token=secret-token");
            span.setAttribute("messaging.kafka.message.key", "private-user");
            span.setAttribute("responses", "private-health");
            span.recordException(new IllegalStateException("secret-token"));
            span.setStatus(StatusCode.ERROR, "private-health");
            span.end();
            var safe = tracing.exported.getFinishedSpanItems().getFirst();
            assertEquals("POST", safe.getAttributes().get(AttributeKey.stringKey("http.method")));
            assertEquals(1, safe.getAttributes().size());
            assertTrue(safe.getEvents().isEmpty());
            assertEquals(StatusCode.ERROR, safe.getStatus().getStatusCode());
            assertEquals("", safe.getStatus().getDescription());
        }
    }
}
