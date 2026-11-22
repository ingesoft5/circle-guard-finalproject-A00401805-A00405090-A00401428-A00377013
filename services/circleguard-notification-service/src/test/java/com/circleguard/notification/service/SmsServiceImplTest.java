/**
 * CG-DOC: fast unit tests for the SMS delivery implementation. Twilio's static
 * entry point is mocked, so the real-account branch stays a pure unit test.
 */
package com.circleguard.notification.service;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmsServiceImplTest {

    private AuditLogService auditLogService;
    private SmsServiceImpl service;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        service = new SmsServiceImpl();
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "accountSid", "AC_MOCK_SID");
        ReflectionTestUtils.setField(service, "authToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(service, "fromNumber", "+15550000000");
    }

    @Test
    void mockAccountSkipsTwilioInitialization() {
        service.init();

        verifyNoInteractions(auditLogService);
    }

    @Test
    void mockAccountAuditsSuccessWithoutCallingTwilio() {
        service.sendAsync("student-1", "message").join();

        verify(auditLogService).logDelivery(eq("student-1"), eq("SMS"), eq("SUCCESS"), anyString());
    }

    @Test
    void realAccountCreatesMessageAndAuditsSuccess() {
        ReflectionTestUtils.setField(service, "accountSid", "AC_REAL_SID");
        MessageCreator creator = mock(MessageCreator.class);
        when(creator.create()).thenReturn(mock(Message.class));

        try (MockedStatic<Message> twilio = Mockito.mockStatic(Message.class)) {
            twilio.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                .thenReturn(creator);

            service.sendAsync("student-2", "message").join();
        }

        verify(creator).create();
        verify(auditLogService).logDelivery(eq("student-2"), eq("SMS"), eq("SUCCESS"), anyString());
    }

    @Test
    void twilioFailureAuditsRetryAndRethrows() {
        ReflectionTestUtils.setField(service, "accountSid", "AC_REAL_SID");
        MessageCreator creator = mock(MessageCreator.class);
        when(creator.create()).thenThrow(new IllegalStateException("Twilio down"));

        try (MockedStatic<Message> twilio = Mockito.mockStatic(Message.class)) {
            twilio.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                .thenReturn(creator);

            assertThatThrownBy(() -> service.sendAsync("student-3", "message"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Twilio down");
        }

        verify(auditLogService).logDelivery(eq("student-3"), eq("SMS"), eq("RETRY"), anyString());
    }

    @Test
    void recoverAuditsFinalFailureAndReturnsFailedFuture() {
        RuntimeException failure = new RuntimeException("Twilio down");

        assertThatThrownBy(() -> service.recover(failure, "student-4", "message").join())
            .isInstanceOf(CompletionException.class)
            .hasCause(failure);

        verify(auditLogService).logDelivery(eq("student-4"), eq("SMS"), eq("FAILED"), isNull());
    }
}
