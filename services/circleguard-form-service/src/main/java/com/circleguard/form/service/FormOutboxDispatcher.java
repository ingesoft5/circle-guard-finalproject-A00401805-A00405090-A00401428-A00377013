/** CG-DOC: Dispatches persisted form outbox events to Kafka. */
package com.circleguard.form.service;

import com.circleguard.form.model.FormOutboxEvent;
import com.circleguard.form.repository.FormOutboxRepository;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.transaction.annotation.Transactional;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class FormOutboxDispatcher {
    // Keep the bounded publication log source consumed by the HU6 Alloy filter.
    private static final Logger log = LoggerFactory.getLogger(FormEventPublisher.class);
    private final FormOutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry meters;
    private final ObjectProvider<Tracer> tracers;
    private final ObjectProvider<Propagator> propagators;
    private final Retry retry;
    private final long timeoutMs;

    public FormOutboxDispatcher(FormOutboxRepository outbox, KafkaTemplate<String, Object> kafka,
                                MeterRegistry meters, ObjectProvider<Tracer> tracers,
                                ObjectProvider<Propagator> propagators,
                                @Value("${form.outbox.ack-timeout-ms:3000}") long timeoutMs,
                                @Value("${form.outbox.retry-attempts:3}") int retryAttempts,
                                @Value("${form.outbox.retry-wait-ms:500}") long retryWaitMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.meters = meters;
        this.tracers = tracers;
        this.propagators = propagators;
        this.timeoutMs = timeoutMs;
        this.retry = Retry.of("formKafkaPublication", RetryConfig.custom()
                .maxAttempts(retryAttempts).waitDuration(Duration.ofMillis(retryWaitMs))
                .retryExceptions(RuntimeException.class).build());
        for (String topic : new String[]{"survey.submitted", "certificate.validated"}) {
            for (String result : new String[]{"success", "failure"}) {
                meters.counter("circleguard.form.event.publications", "event_type", topic, "result", result);
            }
        }
    }

    /** Holds one row lock until Kafka acknowledges or the attempt is deferred. */
    @Transactional("transactionManager")
    public boolean dispatchOne() {
        var candidate = outbox.lockNextDue();
        if (candidate.isEmpty()) return false;
        FormOutboxEvent event = candidate.get();
        try {
            Retry.decorateSupplier(retry, () -> {
                sendAndAwait(event);
                return true;
            }).get();
            meters.counter("circleguard.form.event.publications", "event_type", event.getTopic(), "result", "success").increment();
            outbox.delete(event);
            log.info("event_id={} event_type={} result=success trace_id={}",
                    event.getId(), event.getTopic(), traceId(event));
        } catch (RuntimeException ex) {
            meters.counter("circleguard.form.event.publications", "event_type", event.getTopic(), "result", "failure").increment();
            event.defer(Math.min(60, 1L << Math.min(event.getAttempts(), 6)));
            outbox.save(event);
            log.warn("event_id={} attempts={} event_type={} result=failure trace_id={}",
                    event.getId(), event.getAttempts(), event.getTopic(), traceId(event));
        }
        return true;
    }

    private void sendAndAwait(FormOutboxEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(event.getTopic(), event.getEventKey(), event.getPayload());
        record.headers().add("form-event-id", event.getId().toString().getBytes(StandardCharsets.UTF_8));
        if (event.getTraceparent() != null) {
            record.headers().add("traceparent", event.getTraceparent().getBytes(StandardCharsets.UTF_8));
        }
        Tracer tracer = tracers.getIfAvailable();
        Propagator propagator = propagators.getIfAvailable();
        Span span = null;
        if (tracer != null && propagator != null && event.getTraceparent() != null) {
            span = propagator.extract(Map.of("traceparent", event.getTraceparent()), Map::get)
                    .name("form.outbox.publish").start();
        }
        try {
            if (span != null) {
                try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                    await(record);
                }
            } else {
                await(record);
            }
        } finally {
            if (span != null) span.end();
        }
    }

    private void await(ProducerRecord<String, Object> record) {
        try {
            kafka.send(record).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka acknowledgement interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka acknowledgement unavailable", ex);
        }
    }

    private String traceId(FormOutboxEvent event) {
        String header = event.getTraceparent();
        if (header == null || !header.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) return "none";
        return header.substring(3, 35);
    }
}
