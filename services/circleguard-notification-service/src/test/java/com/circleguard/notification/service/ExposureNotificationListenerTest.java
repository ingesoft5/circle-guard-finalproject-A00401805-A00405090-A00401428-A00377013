/**
 * CG-DOC: pure unit tests for the exposure listener: JSON parsing is real, while
 * the dispatcher and the LMS integration are mocked. No Spring context and no
 * Kafka container are started here, so this class is not tagged as integration.
 */
package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ExposureNotificationListenerTest {

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
    void shouldDispatchAndSyncExposureUsingAnonymousIdAndStatus() {
        // Contract event: form -> survey.submitted -> Promotion ->
        // promotion.status.changed -> Notification -> notification.audit.
        String event = "{\"anonymousId\":\"student-1\",\"status\":\"EXPOSED\"}";

        listener.handleStatusChange(event);

        verify(dispatcher).dispatch("student-1", "EXPOSED");
        verify(lmsService).syncRemoteAttendance("student-1", "EXPOSED");
    }

    @Test
    void shouldIgnoreActiveStatus() {
        listener.handleStatusChange("{\"anonymousId\":\"student-2\",\"status\":\"ACTIVE\"}");

        verifyNoInteractions(dispatcher, lmsService);
    }

    @Test
    void shouldIgnoreEventWithMissingStatus() {
        listener.handleStatusChange("{\"anonymousId\":\"student-3\"}");

        verifyNoInteractions(dispatcher, lmsService);
    }

    @Test
    void shouldIgnoreMalformedEvent() {
        listener.handleStatusChange("{not json");

        verifyNoInteractions(dispatcher, lmsService);
    }
}
