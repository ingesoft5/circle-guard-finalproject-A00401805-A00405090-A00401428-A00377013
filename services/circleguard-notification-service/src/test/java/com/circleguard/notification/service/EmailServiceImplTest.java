/**
 * CG-DOC: Unit tests for the email channel: delivery, retry audit and recovery.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuditLogService auditLogService;

    private EmailServiceImpl emailService() {
        return new EmailServiceImpl(mailSender, auditLogService);
    }

    @Test
    void successfulDeliveryAuditsSuccessWithCorrelationId() {
        CompletableFuture<Void> result = emailService().sendAsync("user-123", "Your status is SUSPECT");

        assertThat(result).isCompleted();
        assertThat(result.join()).isNull();

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logDelivery(eq("user-123"), eq("EMAIL"), eq("SUCCESS"), correlationId.capture());
        assertThat(correlationId.getValue()).isNotBlank();
    }

    @Test
    void failedDeliveryAuditsRetryAndRethrowsForTheRetryInterceptor() {
        doThrow(new RuntimeException("Mail server down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService().sendAsync("user-123", "payload"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Mail server down");

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logDelivery(eq("user-123"), eq("EMAIL"), eq("RETRY"), correlationId.capture());
        assertThat(correlationId.getValue()).isNotBlank();
        verify(auditLogService, org.mockito.Mockito.times(1)).logDelivery(any(), any(), any(), any());
    }

    @Test
    void recoveryAuditsFailedWithoutCorrelationIdAndReturnsFailedFuture() {
        RuntimeException failure = new RuntimeException("Mail server down");

        CompletableFuture<Void> recovered = emailService().recover(failure, "user-123", "payload");

        assertThat(recovered).isCompletedExceptionally();
        assertThatThrownBy(recovered::join).hasCause(failure);
        verify(auditLogService).logDelivery("user-123", "EMAIL", "FAILED", null);
        verifyNoMoreInteractions(auditLogService);
    }
}
