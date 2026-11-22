/**
 * CG-DOC: business service for the notification service: Audit Log Service.
 */
package com.circleguard.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    /**
     * Business meter shared with the monitoring stack: exposed by Micrometer as
     * {@code circleguard_notification_deliveries_total} with a {@code channel} and a
     * {@code result} tag. Every per-channel outcome already funnels through this class,
     * so the counter lives here instead of in each channel implementation.
     */
    static final String DELIVERIES_METRIC = "circleguard.notification.deliveries";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private static final String TOPIC_AUDIT = "notification.audit";

    public void logDelivery(String userId, String channel, String status, String correlationId) {
        log.info("Logging notification audit");
        
        Map<String, Object> auditEvent = new HashMap<>();
        auditEvent.put("eventId", UUID.randomUUID().toString());
        auditEvent.put("timestamp", Instant.now().toString());
        auditEvent.put("userId", userId); // This is an anonymousId, so it's compliant
        auditEvent.put("channel", channel);
        auditEvent.put("status", status);
        auditEvent.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());

        // Only SUCCESS is a delivered notification: RETRY and FAILED are both failures
        // of the delivery attempt from the monitoring stack's point of view.
        meterRegistry.counter(
                DELIVERIES_METRIC,
                "channel", channel != null ? channel.toLowerCase(Locale.ROOT) : "unknown",
                "result", "SUCCESS".equalsIgnoreCase(status) ? "success" : "failure"
        ).increment();

        kafkaTemplate.send(TOPIC_AUDIT, userId, auditEvent);
    }
}
