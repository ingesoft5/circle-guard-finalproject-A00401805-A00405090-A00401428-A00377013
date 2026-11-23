/** CG-DOC: Publishes form events through the outbox pattern. */
package com.circleguard.form.service;

import com.circleguard.form.model.FormOutboxEvent;
import com.circleguard.form.repository.FormOutboxRepository;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** Persists the event in the caller's PostgreSQL transaction. */
@Component
public class FormEventPublisher {
    private final FormOutboxRepository outbox;
    private final ObjectProvider<Tracer> tracers;
    private final ObjectProvider<Propagator> propagators;

    public FormEventPublisher(FormOutboxRepository outbox, ObjectProvider<Tracer> tracers,
                              ObjectProvider<Propagator> propagators) {
        this.outbox = outbox;
        this.tracers = tracers;
        this.propagators = propagators;
    }

    public void publish(String topic, String key, Object event) {
        if (!"survey.submitted".equals(topic) && !"certificate.validated".equals(topic)) {
            throw new IllegalArgumentException("Unsupported Form event type");
        }
        if (!(event instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Form event must be a map");
        }
        Map<String, Object> payload = new HashMap<>();
        values.forEach((name, value) -> payload.put((String) name, value));
        Map<String, String> headers = new HashMap<>();
        Tracer tracer = tracers.getIfAvailable();
        Propagator propagator = propagators.getIfAvailable();
        if (tracer != null && propagator != null && tracer.currentSpan() != null) {
            propagator.inject(tracer.currentSpan().context(), headers, Map::put);
        }
        outbox.save(new FormOutboxEvent(topic, key, payload, headers.get("traceparent")));
    }
}
