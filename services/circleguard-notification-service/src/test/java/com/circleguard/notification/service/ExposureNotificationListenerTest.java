/**
 * CG-DOC: Unit or integration tests for Exposure Notification Listener Test in the notification service.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ExposureNotificationListenerTest {

    @Autowired
    private ExposureNotificationListener listener;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private NotificationDispatcher dispatcher;

    @MockitoBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @MockitoBean
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private SmsService smsService;

    @MockitoBean
    private PushService pushService;

    @Test
    void shouldHandleStatusChangeEventWithoutError() {
        String mockEvent = "{\"userId\": \"user-123\", \"newStatus\": \"EXPOSED\"}";
        assertDoesNotThrow(() -> listener.handleStatusChange(mockEvent));
    }
}
