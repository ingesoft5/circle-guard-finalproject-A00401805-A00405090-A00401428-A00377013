/**
 * CG-DOC: fast unit tests for the email delivery implementation; no Spring context,
 * no retry proxy, so both the happy path and the failure path are exercised directly.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private AuditLogService auditLogService;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        auditLogService = mock(AuditLogService.class);
        service = new EmailServiceImpl(mailSender, auditLogService);
    }

    @Test
    void sendsMailAndAuditsSuccess() {
        service.sendAsync("student-1", "status changed").join();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("student-1@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("CircleGuard Health Alert");
        assertThat(sent.getValue().getText()).isEqualTo("status changed");
        verify(auditLogService).logDelivery(eq("student-1"), eq("EMAIL"), eq("SUCCESS"), anyString());
    }

    @Test
    void auditsRetryAndRethrowsWhenSmtpFails() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendAsync("student-2", "message"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("SMTP down");

        verify(auditLogService).logDelivery(eq("student-2"), eq("EMAIL"), eq("RETRY"), anyString());
    }

    @Test
    void recoverAuditsFinalFailureAndReturnsFailedFuture() {
        RuntimeException failure = new RuntimeException("SMTP down");

        assertThatThrownBy(() -> service.recover(failure, "student-3", "message").join())
            .isInstanceOf(CompletionException.class)
            .hasCause(failure);

        verify(auditLogService).logDelivery(eq("student-3"), eq("EMAIL"), eq("FAILED"), isNull());
    }
}
