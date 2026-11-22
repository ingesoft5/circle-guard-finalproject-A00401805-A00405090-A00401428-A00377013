package com.circleguard.form.observability;

import com.circleguard.form.service.FormEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class FormEventPublisherTest {
    @ParameterizedTest
    @ValueSource(strings = {"survey.submitted", "certificate.validated"})
    void countsAcknowledgedSuccessOnly(String topic, CapturedOutput output) {
        var meters = new SimpleMeterRegistry();
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var future = new CompletableFuture<SendResult<String, Object>>();
        when(kafka.send(topic, "private-user", Map.of("responses", "private-health"))).thenReturn(future);
        try (var tracing = new TracingFixture()) {
            var beans = new StaticListableBeanFactory(Map.of("tracer", tracing.tracer));
            var publisher = new FormEventPublisher(kafka, meters, beans.getBeanProvider(Tracer.class));
            var parent = tracing.tracer.nextSpan().name("survey-request").start();
            try (var scope = tracing.tracer.withSpan(parent)) {
                publisher.publish(topic, "private-user", Map.of("responses", "private-health"));
            }
            assertEquals(0, count(meters, topic, "success"));
            future.complete(null); // Callback may run after the HTTP scope has closed.
            future.complete(null);
            assertEquals(1, count(meters, topic, "success"));
            assertEquals(0, count(meters, topic, "failure"));
            assertTrue(output.getOut().contains(parent.context().traceId()));
            parent.end();
        }
        assertFalse(output.getAll().contains("private-user"));
        assertFalse(output.getAll().contains("private-health"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"survey.submitted", "certificate.validated"})
    void countsAsyncFailureWithoutLeakingPayloadOrException(String topic, CapturedOutput output) {
        var meters = new SimpleMeterRegistry();
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var future = new CompletableFuture<SendResult<String, Object>>();
        when(kafka.send(topic, "private-user", "private-health")).thenReturn(future);
        var publisher = new FormEventPublisher(kafka, meters,
                new StaticListableBeanFactory().getBeanProvider(Tracer.class));
        publisher.publish(topic, "private-user", "private-health");
        future.completeExceptionally(new IllegalStateException("secret-token"));
        assertEquals(1, count(meters, topic, "failure"));
        assertEquals(0, count(meters, topic, "success"));
        assertFalse(output.getAll().contains("private-user"));
        assertFalse(output.getAll().contains("private-health"));
        assertFalse(output.getAll().contains("secret-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"survey.submitted", "certificate.validated"})
    void recordsAndRethrowsSynchronousFailure(String topic) {
        var meters = new SimpleMeterRegistry();
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var error = new IllegalStateException("broker unavailable");
        when(kafka.send(topic, "key", "payload")).thenThrow(error);
        var publisher = new FormEventPublisher(kafka, meters,
                new StaticListableBeanFactory().getBeanProvider(Tracer.class));
        assertSame(error, assertThrows(IllegalStateException.class,
                () -> publisher.publish(topic, "key", "payload")));
        assertEquals(1, count(meters, topic, "failure"));
    }

    @Test
    void rejectsUnboundedEventTypesBeforeSending() {
        var meters = new SimpleMeterRegistry();
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var publisher = new FormEventPublisher(kafka, meters,
                new StaticListableBeanFactory().getBeanProvider(Tracer.class));
        assertThrows(IllegalArgumentException.class, () -> publisher.publish("arbitrary", "key", "payload"));
        verifyNoInteractions(kafka);
    }

    private double count(SimpleMeterRegistry meters, String topic, String result) {
        return meters.get("circleguard.form.event.publications")
                .tags("event_type", topic, "result", result).counter().count();
    }
}
