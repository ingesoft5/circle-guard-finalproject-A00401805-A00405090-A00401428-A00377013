package com.circleguard.form.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Observes delivery acknowledgements without changing event contracts or retries. */
@Component
public class FormEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(FormEventPublisher.class);
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry meters;
    private final ObjectProvider<Tracer> tracers;

    public FormEventPublisher(KafkaTemplate<String, Object> kafka, MeterRegistry meters,
                              ObjectProvider<Tracer> tracers) {
        this.kafka = kafka;
        this.meters = meters;
        this.tracers = tracers;
        for (String topic : new String[]{"survey.submitted", "certificate.validated"}) {
            for (String result : new String[]{"success", "failure"}) {
                meters.counter("circleguard.form.event.publications", "event_type", topic, "result", result);
            }
        }
    }

    public void publish(String topic, String key, Object event) {
        if (!"survey.submitted".equals(topic) && !"certificate.validated".equals(topic)) {
            throw new IllegalArgumentException("Unsupported Form event type");
        }
        Tracer tracer = tracers.getIfAvailable();
        String traceId = tracer != null && tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId() : "none";
        try {
            kafka.send(topic, key, event).whenComplete((ack, error) ->
                    record(topic, error == null ? "success" : "failure", traceId));
        } catch (RuntimeException error) {
            record(topic, "failure", traceId);
            throw error;
        }
    }

    private void record(String topic, String result, String traceId) {
        meters.counter("circleguard.form.event.publications", "event_type", topic, "result", result).increment();
        // Do not log exception messages, Kafka keys, event payloads or attachment paths.
        if ("failure".equals(result)) {
            log.warn("event_type={} result={} trace_id={}", topic, result, traceId);
        } else {
            log.info("event_type={} result={} trace_id={}", topic, result, traceId);
        }
    }
}
