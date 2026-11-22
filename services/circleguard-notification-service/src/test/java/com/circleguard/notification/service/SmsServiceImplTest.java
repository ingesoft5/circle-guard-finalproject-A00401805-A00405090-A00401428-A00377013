/**
 * CG-DOC: Unit tests for the SMS channel: mock-mode delivery, init guard and recovery.
 */
package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SmsServiceImplTest {

    @Mock
    private AuditLogService auditLogService;

    private SmsServiceImpl smsService(String accountSid) {
        SmsServiceImpl service = new SmsServiceImpl();
        ReflectionTestUtils.setField(service, "accountSid", accountSid);
        ReflectionTestUtils.setField(service, "authToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(service, "fromNumber", "+15550000000");
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        return service;
    }

    @Test
    void mockAccountShortCircuitsDeliveryAndAuditsSuccess() {
        SmsServiceImpl service = smsService("AC_MOCK_SID");
        service.init();

        CompletableFuture<Void> result = service.sendAsync("user-123", "Your status is SUSPECT");

        assertThat(result).isCompleted();
        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logDelivery(eq("user-123"), eq("SMS"), eq("SUCCESS"), correlationId.capture());
        assertThat(correlationId.getValue()).isNotBlank();
    }

    @Test
    void initSkipsTwilioWhenTheAccountIsMocked() {
        SmsServiceImpl mocked = smsService("AC_MOCK_SID");
        SmsServiceImpl configured = smsService("ACrealaccountsid");

        // Neither call performs network I/O: Twilio.init only stores the credentials.
        mocked.init();
        configured.init();

        assertThat(ReflectionTestUtils.getField(configured, "accountSid")).isEqualTo("ACrealaccountsid");
    }

    @Test
    void recoveryAuditsFailedWithoutCorrelationIdAndReturnsFailedFuture() {
        SmsServiceImpl service = smsService("AC_MOCK_SID");
        RuntimeException failure = new RuntimeException("Twilio down");

        CompletableFuture<Void> recovered = service.recover(failure, "user-123", "payload");

        assertThat(recovered).isCompletedExceptionally();
        assertThatThrownBy(recovered::join).hasCause(failure);
        verify(auditLogService).logDelivery("user-123", "SMS", "FAILED", null);
        verifyNoMoreInteractions(auditLogService);
    }
}
