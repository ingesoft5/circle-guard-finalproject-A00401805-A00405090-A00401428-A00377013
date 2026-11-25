/**
 * CG-DOC: pure unit tests for the multi-channel dispatcher. All collaborators
 * are mocked and the service is instantiated directly: no Spring context, no
 * container and no external channel is involved.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    private EmailService emailService;
    private SmsService smsService;
    private PushService pushService;
    private TemplateService templateService;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        smsService = mock(SmsService.class);
        pushService = mock(PushService.class);
        templateService = mock(TemplateService.class);
        dispatcher = new NotificationDispatcher(emailService, smsService, pushService, templateService);
    }

    @Test
    void shouldDispatchToAllChannelsConcurrently() {
        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatch("user-123", "Your health status has changed.");

        verify(emailService, timeout(1000)).sendAsync(org.mockito.ArgumentMatchers.eq("user-123"), any());
        verify(smsService, timeout(1000)).sendAsync(org.mockito.ArgumentMatchers.eq("user-123"), any());
        verify(pushService, timeout(1000)).sendAsync(org.mockito.ArgumentMatchers.eq("user-123"), any(), any());
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

        dispatcher.dispatch("user-123", "PROBABLE");

        // A failed channel must not stop the other two from being attempted.
        verify(emailService, timeout(1000)).sendAsync(org.mockito.ArgumentMatchers.eq("user-123"), any());
        verify(smsService, timeout(1000)).sendAsync(org.mockito.ArgumentMatchers.eq("user-123"), any());
        verify(pushService, timeout(1000)).sendAsync(org.mockito.ArgumentMatchers.eq("user-123"), any(), any());
    }
}
