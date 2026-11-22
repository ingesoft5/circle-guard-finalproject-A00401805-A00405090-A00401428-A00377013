package com.circleguard.notification.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class ExposureNotificationPrivacyTest {
    @Test
    void statusChangeUsesPayloadButDoesNotLogIt() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExposureNotificationListener.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var dispatcher = mock(NotificationDispatcher.class);
            var lms = mock(LmsService.class);
            var listener = new ExposureNotificationListener(dispatcher, new ObjectMapper(), lms);
            listener.handleStatusChange("{\"anonymousId\":\"private-user\",\"status\":\"SUSPECT\","
                    + "\"note\":\"private-health\"}");
            verify(dispatcher).dispatch("private-user", "SUSPECT");
            verify(lms).syncRemoteAttendance("private-user", "SUSPECT");
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + b);
            assertFalse(logs.contains("private-user"));
            assertFalse(logs.contains("private-health"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
