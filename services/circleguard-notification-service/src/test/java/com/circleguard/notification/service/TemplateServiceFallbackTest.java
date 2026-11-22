/**
 * CG-DOC: Unit test for the FreeMarker fallback used when the template cannot be rendered.
 */
package com.circleguard.notification.service;

import freemarker.template.Configuration;
import freemarker.template.TemplateNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceFallbackTest {

    @Mock
    private Configuration freemarkerConfiguration;

    @Test
    void fallsBackToAPlainTextEmailWhenTheTemplateCannotBeLoaded() throws Exception {
        when(freemarkerConfiguration.getTemplate(eq("health_alert.ftl")))
                .thenThrow(new TemplateNotFoundException("health_alert.ftl", null, null));
        TemplateService templateService = new TemplateService(freemarkerConfiguration);

        String content = templateService.generateEmailContent("SUSPECT", "anon-42");

        assertThat(content).isEqualTo(
                "CircleGuard Health Update: Your status is now SUSPECT. Please check the app for instructions.");
    }
}
