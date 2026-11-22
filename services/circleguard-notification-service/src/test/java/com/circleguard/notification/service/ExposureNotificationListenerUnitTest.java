/**
 * CG-DOC: Unit tests for the exposure listener status filter and its defensive floor.
 */
package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExposureNotificationListenerUnitTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Mock
    private LmsService lmsService;

    private ExposureNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new ExposureNotificationListener(dispatcher, new ObjectMapper(), lmsService);
    }

    @Test
    void dispatchesAndSyncsLmsForAnExposureStatus() {
        listener.handleStatusChange("{\"anonymousId\":\"anon-42\",\"status\":\"PROBABLE\"}");

        verify(dispatcher).dispatch("anon-42", "PROBABLE");
        verify(lmsService).syncRemoteAttendance("anon-42", "PROBABLE");
    }

    @Test
    void ignoresTheActiveStatusBecauseThatIsNotAnExposure() {
        listener.handleStatusChange("{\"anonymousId\":\"anon-42\",\"status\":\"ACTIVE\"}");

        verify(dispatcher, never()).dispatch(any(), any());
        verify(lmsService, never()).syncRemoteAttendance(any(), any());
    }

    @Test
    void ignoresAnEventWithoutStatusBecauseItDefaultsToUnknown() {
        listener.handleStatusChange("{\"anonymousId\":\"anon-42\"}");

        verify(dispatcher, never()).dispatch(any(), any());
        verify(lmsService, never()).syncRemoteAttendance(any(), any());
    }

    @Test
    void swallowsAMalformedEventWithoutDispatching() {
        assertThatCode(() -> listener.handleStatusChange("not-a-json-payload"))
                .doesNotThrowAnyException();

        verify(dispatcher, never()).dispatch(any(), any());
    }
}
