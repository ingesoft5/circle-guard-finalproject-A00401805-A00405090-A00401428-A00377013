/**
 * CG-DOC: Resilient Kafka audit event publisher for identity vault lookups (Resilience4j CircuitBreaker + Retry).
 */
package com.circleguard.identity.service;

import com.circleguard.identity.event.IdentityAccessEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdentityAuditPublisher {

    public static final String AUDIT_TOPIC = "audit.identity.accessed";
    private static final String AUDIT_PUBLISHED_METRIC = "circleguard_identity_audit_events_total";
    private static final String AUDIT_FALLBACK_METRIC = "circleguard_identity_audit_fallback_total";
    private static final Logger log = LoggerFactory.getLogger(IdentityAuditPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Autowired
    public IdentityAuditPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(kafkaTemplate,
                meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new) : new SimpleMeterRegistry());
    }

    public IdentityAuditPublisher(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    @CircuitBreaker(name = "identityAuditKafka", fallbackMethod = "fallbackAuditEvent")
    @Retry(name = "identityAuditKafka")
    public void publishAuditEvent(IdentityAccessEvent event) {
        kafkaTemplate.send(AUDIT_TOPIC, event);
        meterRegistry.counter(AUDIT_PUBLISHED_METRIC, "result", "published").increment();
        log.info("Published identity audit event: eventId={}, anonymousId={}, status={}",
                event.getEventId(),
                event.getPayload() != null ? event.getPayload().getAnonymousId() : null,
                event.getPayload() != null ? event.getPayload().getAccessStatus() : null);
    }

    public void fallbackAuditEvent(IdentityAccessEvent event, Throwable ex) {
        meterRegistry.counter(AUDIT_FALLBACK_METRIC).increment();
        meterRegistry.counter(AUDIT_PUBLISHED_METRIC, "result", "fallback").increment();
        log.warn("Identity audit Kafka circuit breaker / fallback activated for eventId={}, anonymousId={}, status={}: {}",
                event != null ? event.getEventId() : "unknown",
                event != null && event.getPayload() != null ? event.getPayload().getAnonymousId() : null,
                event != null && event.getPayload() != null ? event.getPayload().getAccessStatus() : null,
                ex != null ? ex.getMessage() : "unknown error");
    }
}
