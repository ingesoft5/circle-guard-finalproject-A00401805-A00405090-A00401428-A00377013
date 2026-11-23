package com.circleguard.form.observability;

import com.circleguard.form.model.FormOutboxEvent;
import com.circleguard.form.repository.FormOutboxRepository;
import com.circleguard.form.service.FormEventPublisher;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormEventPublisherTest {
    @Test
    void queuesBothEventTypesWithoutSendingToKafka() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        var empty = new StaticListableBeanFactory();
        var publisher = new FormEventPublisher(outbox, empty.getBeanProvider(Tracer.class),
                empty.getBeanProvider(Propagator.class));
        publisher.publish("survey.submitted", "user-1", Map.of("hasSymptoms", true));
        publisher.publish("certificate.validated", "user-1", Map.of("status", "APPROVED"));
        ArgumentCaptor<FormOutboxEvent> rows = ArgumentCaptor.forClass(FormOutboxEvent.class);
        verify(outbox, times(2)).save(rows.capture());
        assertEquals("survey.submitted", rows.getAllValues().get(0).getTopic());
        assertEquals("certificate.validated", rows.getAllValues().get(1).getTopic());
        assertNotEquals(rows.getAllValues().get(0).getId(), rows.getAllValues().get(1).getId());
    }

    @Test
    void rejectsUnknownEventBeforeQueueing() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        var empty = new StaticListableBeanFactory();
        var publisher = new FormEventPublisher(outbox, empty.getBeanProvider(Tracer.class),
                empty.getBeanProvider(Propagator.class));
        assertThrows(IllegalArgumentException.class, () -> publisher.publish("arbitrary", "key", Map.of()));
        verifyNoInteractions(outbox);
    }
}
