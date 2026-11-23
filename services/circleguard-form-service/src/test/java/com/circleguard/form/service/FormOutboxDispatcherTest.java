package com.circleguard.form.service;

import com.circleguard.form.model.FormOutboxEvent;
import com.circleguard.form.repository.FormOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FormOutboxDispatcherTest {
    @Test
    void acknowledgedEventIsRemovedAndKeepsStableEventId() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var meters = new SimpleMeterRegistry();
        var event = new FormOutboxEvent("survey.submitted", "user-1", Map.of("hasSymptoms", true), null);
        when(outbox.lockNextDue()).thenReturn(Optional.of(event));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        FormOutboxDispatcher dispatcher = dispatcher(outbox, kafka, meters);

        assertTrue(dispatcher.dispatchOne());
        verify(outbox).delete(event);
        verify(kafka).send(org.mockito.ArgumentMatchers.<ProducerRecord<String, Object>>argThat(record -> event.getId().toString().equals(
                new String(record.headers().lastHeader("form-event-id").value()))));
        assertEquals(1, count(meters, "success"));
    }

    @Test
    void failedAcknowledgementRetriesThenKeepsEventForRecovery() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var meters = new SimpleMeterRegistry();
        var event = new FormOutboxEvent("survey.submitted", "user-1", Map.of("hasSymptoms", true), null);
        when(outbox.lockNextDue()).thenReturn(Optional.of(event));
        CompletableFuture<SendResult<String, Object>> failed =
                CompletableFuture.failedFuture(new IllegalStateException("broker down"));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(failed, failed, CompletableFuture.completedFuture(null));
        FormOutboxDispatcher dispatcher = dispatcher(outbox, kafka, meters);

        assertTrue(dispatcher.dispatchOne());
        assertEquals(1, event.getAttempts());
        assertEquals(1, count(meters, "failure"));
        verify(kafka, times(2)).send(any(ProducerRecord.class));
        verify(outbox, never()).delete(event);

        assertTrue(dispatcher.dispatchOne());
        verify(outbox).delete(event);
        assertEquals(1, count(meters, "success"));
    }

    @Test
    void emptyQueueDoesNotCallKafka() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        when(outbox.lockNextDue()).thenReturn(Optional.empty());
        assertFalse(dispatcher(outbox, kafka, new SimpleMeterRegistry()).dispatchOne());
        verifyNoInteractions(kafka);
    }

    private FormOutboxDispatcher dispatcher(FormOutboxRepository outbox, KafkaTemplate<String, Object> kafka,
                                             SimpleMeterRegistry meters) {
        var empty = new StaticListableBeanFactory();
        return new FormOutboxDispatcher(outbox, kafka, meters, empty.getBeanProvider(Tracer.class),
                empty.getBeanProvider(Propagator.class), 100, 2, 0);
    }

    private double count(SimpleMeterRegistry meters, String result) {
        return meters.get("circleguard.form.event.publications")
                .tags("event_type", "survey.submitted", "result", result).counter().count();
    }
}
