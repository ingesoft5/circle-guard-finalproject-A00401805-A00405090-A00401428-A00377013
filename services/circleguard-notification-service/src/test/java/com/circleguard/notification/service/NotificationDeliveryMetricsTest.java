/**
 * CG-DOC: Unit tests for the notification delivery business meter (DCG-359).
 */
package com.circleguard.notification.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that the notification audit path publishes the shared business meter
 * {@code circleguard_notification_deliveries_total} with the {@code result} and
 * {@code channel} tags. The Prometheus exposition is asserted too, because that is
 * exactly what the monitoring stack scrapes from {@code /actuator/prometheus}.
 */
class NotificationDeliveryMetricsTest {

    private static final String METRIC = "circleguard.notification.deliveries";
    private static final String EXPOSITION_NAME = "circleguard_notification_deliveries_total";
    private static final String TOPIC_AUDIT = "notification.audit";

    private KafkaTemplate<String, Object> kafkaTemplate;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
    }

    private AuditLogService service() {
        return new AuditLogService(kafkaTemplate, meterRegistry);
    }

    private double count(MeterRegistry registry, String channel, String result) {
        // Micrometer registers a meter lazily on first increment, so an absent series
        // means "never incremented" rather than a failure.
        Counter counter = registry.find(METRIC).tag("channel", channel).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void successAuditIncrementsSuccessCounterForItsChannelOnly() {
        service().logDelivery("anon-1", "EMAIL", "SUCCESS", "corr-1");

        assertEquals(1.0, count(meterRegistry, "email", "success"), 0.0001);
        assertEquals(0.0, count(meterRegistry, "email", "failure"), 0.0001);
        assertEquals(0.0, count(meterRegistry, "sms", "success"), 0.0001);
    }

    @Test
    void retryAuditCountsAsFailureForItsChannel() {
        service().logDelivery("anon-1", "SMS", "RETRY", "corr-2");

        assertEquals(1.0, count(meterRegistry, "sms", "failure"), 0.0001);
        assertEquals(0.0, count(meterRegistry, "sms", "success"), 0.0001);
    }

    @Test
    void failedAuditCountsAsFailureForItsChannel() {
        service().logDelivery("anon-1", "PUSH", "FAILED", null);

        assertEquals(1.0, count(meterRegistry, "push", "failure"), 0.0001);
        assertEquals(0.0, count(meterRegistry, "push", "success"), 0.0001);
    }

    @Test
    void consecutiveAuditsAccumulatePerChannelAndResult() {
        AuditLogService audit = service();
        audit.logDelivery("anon-1", "EMAIL", "SUCCESS", "c1");
        audit.logDelivery("anon-2", "EMAIL", "SUCCESS", "c2");
        audit.logDelivery("anon-3", "EMAIL", "RETRY", "c3");
        audit.logDelivery("anon-4", "PUSH", "SUCCESS", "c4");

        assertEquals(2.0, count(meterRegistry, "email", "success"), 0.0001);
        assertEquals(1.0, count(meterRegistry, "email", "failure"), 0.0001);
        assertEquals(1.0, count(meterRegistry, "push", "success"), 0.0001);
        assertEquals(0.0, count(meterRegistry, "push", "failure"), 0.0001);
    }

    @Test
    void auditStillPublishesTheDeliveryEventToKafkaTopic() {
        service().logDelivery("anon-7", "LMS", "SUCCESS", "corr-7");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC_AUDIT), eq("anon-7"), payload.capture());

        assertTrue(payload.getValue() instanceof Map, "audit payload must stay the Map event");
        Map<?, ?> event = (Map<?, ?>) payload.getValue();
        assertEquals("anon-7", event.get("userId"));
        assertEquals("LMS", event.get("channel"));
        assertEquals("SUCCESS", event.get("status"));
        assertEquals("corr-7", event.get("correlationId"));
        assertNotNull(event.get("eventId"));
        assertNotNull(event.get("timestamp"));
    }

    @Test
    void auditGeneratesCorrelationIdWhenChannelCallerHasNone() {
        service().logDelivery("anon-8", "EMAIL", "FAILED", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC_AUDIT), anyString(), payload.capture());

        Map<?, ?> event = (Map<?, ?>) payload.getValue();
        Object correlationId = event.get("correlationId");
        assertNotNull(correlationId);
        assertTrue(((String) correlationId).length() > 0);
    }

    @Test
    void publishesSharedPrometheusExposition() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new AuditLogService(kafkaTemplate, prometheus).logDelivery("anon-9", "EMAIL", "SUCCESS", "corr-9");

        String scrape = prometheus.scrape();
        assertTrue(scrape.contains(EXPOSITION_NAME),
                "Prometheus exposition must contain " + EXPOSITION_NAME + " but was:\n" + scrape);
        assertTrue(scrape.contains("result=\"success\""),
                "Exposition must carry result=\"success\" but was:\n" + scrape);
        assertTrue(scrape.contains("channel=\"email\""),
                "Exposition must carry channel=\"email\" but was:\n" + scrape);
    }
}
