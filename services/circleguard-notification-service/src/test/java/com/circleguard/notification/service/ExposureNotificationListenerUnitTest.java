/**
 * CG-DOC: plain unit tests for the exposure listener, complementing the tagged
 * Spring context test. A real ObjectMapper keeps the JSON parsing meaningful while
 * the dispatcher and LMS service are mocked.
 */
package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ExposureNotificationListenerUnitTest {

    private NotificationDispatcher dispatcher;
    private LmsService lmsService;
    private ExposureNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        lmsService = mock(LmsService.class);
        listener = new ExposureNotificationListener(dispatcher, new ObjectMapper(), lmsService);
    }

    @Test
    void nonActiveStatusIsDispatchedAndSyncedToLms() {
        listener.handleStatusChange("{\"anonymousId\":\"student-1\",\"status\":\"EXPOSED\"}");

        verify(dispatcher).dispatch("student-1", "EXPOSED");
        verify(lmsService).syncRemoteAttendance("student-1", "EXPOSED");
    }

    @Test
    void activeStatusDoesNotTriggerNotifications() {
        listener.handleStatusChange("{\"anonymousId\":\"student-2\",\"status\":\"ACTIVE\"}");

        verifyNoInteractions(dispatcher, lmsService);
    }

    @Test
    void missingStatusDefaultsToUnknownAndDoesNotTriggerNotifications() {
        listener.handleStatusChange("{\"anonymousId\":\"student-3\"}");

        verifyNoInteractions(dispatcher, lmsService);
    }

    @Test
    void malformedEventIsSwallowedWithoutDispatching() {
        assertDoesNotThrow(() -> listener.handleStatusChange("{not json"));

        verifyNoInteractions(dispatcher, lmsService);
    }
}
