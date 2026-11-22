/**
 * CG-DOC: Unit tests for the audit log publisher topic contract.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void publishesEveryDeliveryToTheNotificationAuditTopic() {
        AuditLogService auditLogService = new AuditLogService(kafkaTemplate);

        auditLogService.logDelivery("anonymous-123", "EMAIL", "SUCCESS", "corr-1");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("notification.audit"), eq("anonymous-123"), payload.capture());
        Map<String, Object> event = (Map<String, Object>) payload.getValue();
        assertThat(event)
                .containsEntry("userId", "anonymous-123")
                .containsEntry("channel", "EMAIL")
                .containsEntry("status", "SUCCESS")
                .containsEntry("correlationId", "corr-1")
                .containsKeys("eventId", "timestamp");
        assertThat((String) event.get("eventId")).isNotBlank();
        assertThat((String) event.get("timestamp")).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatesACorrelationIdWhenTheChannelHasNone() {
        AuditLogService auditLogService = new AuditLogService(kafkaTemplate);

        auditLogService.logDelivery("anonymous-123", "PUSH", "FAILED", null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("notification.audit"), any(), payload.capture());
        Map<String, Object> event = (Map<String, Object>) payload.getValue();
        assertThat(event).containsEntry("status", "FAILED");
        assertThat((String) event.get("correlationId")).isNotBlank();
    }
}
