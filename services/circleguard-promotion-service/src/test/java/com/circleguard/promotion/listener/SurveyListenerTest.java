/**
 * CG-DOC: Unit or integration tests for Survey Listener Test in the promotion service.
 */
package com.circleguard.promotion.listener;

import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class SurveyListenerTest {

    @Mock
    private HealthStatusService healthStatusService;

    @InjectMocks
    private SurveyListener surveyListener;

    @Test
    void onSurveySubmitted_WithSymptoms_PromotesToSuspect() {
        Map<String, Object> event = Map.of(
            "anonymousId", "tester-123",
            "hasSymptoms", true
        );

        surveyListener.onSurveySubmitted(event);

        verify(healthStatusService).updateStatus("tester-123", "SUSPECT");
    }

    @Test
    void onSurveySubmitted_WithoutSymptoms_DoesNothing() {
        Map<String, Object> event = Map.of(
            "anonymousId", "tester-123",
            "hasSymptoms", false
        );

        surveyListener.onSurveySubmitted(event);

        verify(healthStatusService, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void surveyFailureDoesNotLogEventOrExceptionMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(SurveyListener.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new IllegalStateException("private-health"))
                    .when(healthStatusService).updateStatus("private-user", "SUSPECT");
            surveyListener.onSurveySubmitted(Map.of("anonymousId", "private-user",
                    "hasSymptoms", true, "notes", "private-health"));
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
