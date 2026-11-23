/**
 * CG-DOC: Unit or integration tests for Notification Dispatcher Test in the notification service.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatcherTest {

    @Autowired
    private NotificationDispatcher dispatcher;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private SmsService smsService;

    @MockitoBean
    private TemplateService templateService;

    @MockitoBean
    private PushService pushService;

    @Test
    void shouldDispatchToAllChannelsConcurrently() throws Exception {
        // Setup slow services to test concurrency
        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatch("user-123", "Your health status has changed.");

        verify(emailService, timeout(1000)).sendAsync(eq("user-123"), any());
        verify(smsService, timeout(1000)).sendAsync(eq("user-123"), any());
        verify(pushService, timeout(1000)).sendAsync(eq("user-123"), any(), any());
    }

    @Test
    void shouldRouteEachChannelItsOwnRenderedContent() {
        when(templateService.generateEmailContent("PROBABLE", "user-123")).thenReturn("email-body");
        when(templateService.generatePushContent("PROBABLE")).thenReturn("push-body");
        when(templateService.generatePushMetadata("PROBABLE")).thenReturn(Map.of("url", "circleguard://guidelines"));
        when(templateService.generateSmsContent("PROBABLE")).thenReturn("sms-body");
        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatch("user-123", "PROBABLE");

        verify(emailService, timeout(1000)).sendAsync("user-123", "email-body");
        verify(smsService, timeout(1000)).sendAsync("user-123", "sms-body");
        verify(pushService, timeout(1000))
                .sendAsync("user-123", "push-body", Map.of("url", "circleguard://guidelines"));
    }

    @Test
    void shouldContainAChannelFailureInsteadOfPropagatingIt() {
        when(emailService.sendAsync(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("mail down")));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> dispatcher.dispatch("user-123", "PROBABLE"));
    }
}
