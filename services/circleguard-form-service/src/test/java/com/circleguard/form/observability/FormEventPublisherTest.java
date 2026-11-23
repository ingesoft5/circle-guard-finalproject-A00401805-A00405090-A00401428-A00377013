package com.circleguard.form.observability;

import com.circleguard.form.model.FormOutboxEvent;
import com.circleguard.form.repository.FormOutboxRepository;
import com.circleguard.form.service.FormEventPublisher;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void rejectsNonMapEventBeforeQueueing() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        var empty = new StaticListableBeanFactory();
        var publisher = new FormEventPublisher(outbox, empty.getBeanProvider(Tracer.class),
                empty.getBeanProvider(Propagator.class));
        assertThrows(IllegalArgumentException.class,
                () -> publisher.publish("survey.submitted", "user-1", "not-a-map"));
        verifyNoInteractions(outbox);
    }

    @Test
    void injectsTraceparentWhenActiveTraceExists() {
        FormOutboxRepository outbox = mock(FormOutboxRepository.class);
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        Propagator propagator = mock(Propagator.class);
        doAnswer(invocation -> {
            Map<String, String> carrier = invocation.getArgument(1);
            carrier.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
            return null;
        }).when(propagator).inject(any(), any(), any());

        var beans = new StaticListableBeanFactory();
        beans.addBean("tracer", tracer);
        beans.addBean("propagator", propagator);
        var publisher = new FormEventPublisher(outbox, beans.getBeanProvider(Tracer.class),
                beans.getBeanProvider(Propagator.class));

        publisher.publish("survey.submitted", "user-1", Map.of("hasSymptoms", true));

        ArgumentCaptor<FormOutboxEvent> row = ArgumentCaptor.forClass(FormOutboxEvent.class);
        verify(outbox).save(row.capture());
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                row.getValue().getTraceparent());
    }
}
