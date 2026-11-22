/**
 * CG-DOC: plain unit tests for the remaining TemplateService branches, in particular
 * the fallback when FreeMarker cannot render the configured template.
 */
package com.circleguard.notification.service;

import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateServiceUnitTest {

    private Configuration freemarkerConfig;
    private TemplateService service;

    @BeforeEach
    void setUp() {
        freemarkerConfig = mock(Configuration.class);
        service = new TemplateService(freemarkerConfig);
        ReflectionTestUtils.setField(service, "testingUrl", "https://circleguard.example.com/testing");
        ReflectionTestUtils.setField(service, "isolationUrl", "https://circleguard.example.com/isolation");
        ReflectionTestUtils.setField(service, "guidelinesDeepLink", "circleguard://guidelines");
    }

    @Test
    void emailContentFallsBackWhenTemplateCannotBeRendered() throws Exception {
        when(freemarkerConfig.getTemplate("health_alert.ftl")).thenThrow(new IOException("template missing"));

        assertThat(service.generateEmailContent("SUSPECT", "student-1"))
            .isEqualTo("CircleGuard Health Update: Your status is now SUSPECT. Please check the app for instructions.");
    }

    @Test
    void pushContentCoversSuspectedProbableAndDefaultStatuses() {
        assertThat(service.generatePushContent("SUSPECT")).contains("isolation steps");
        assertThat(service.generatePushContent("PROBABLE")).contains("Monitor symptoms");
        assertThat(service.generatePushContent("ACTIVE")).contains("ACTIVE");
    }

    @Test
    void pushMetadataOnlyCarriesGuidelinesForAlertStatuses() {
        assertThat(service.generatePushMetadata("SUSPECT")).isEqualTo(Map.of("url", "circleguard://guidelines"));
        assertThat(service.generatePushMetadata("PROBABLE")).isEqualTo(Map.of("url", "circleguard://guidelines"));
        assertThat(service.generatePushMetadata("ACTIVE")).isEmpty();
    }

    @Test
    void smsContentIncludesStatusAndEmailHint() {
        assertThat(service.generateSmsContent("EXPOSED"))
            .contains("EXPOSED")
            .contains("check your email");
    }
}
