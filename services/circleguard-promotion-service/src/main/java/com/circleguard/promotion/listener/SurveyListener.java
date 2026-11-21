/**
 * CG-DOC: Java source for the promotion service: Survey Listener.
 */
package com.circleguard.promotion.listener;

import com.circleguard.promotion.service.HealthStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SurveyListener {
    private final HealthStatusService healthStatusService;

    @KafkaListener(topics = "survey.submitted", groupId = "promotion-service-group")
    public void onSurveySubmitted(Map<String, Object> event) {
        log.info("Received survey submission event");
        
        try {
            String anonymousId = (String) event.get("anonymousId");
            Boolean hasSymptoms = (Boolean) event.get("hasSymptoms");
            
            if (anonymousId != null && Boolean.TRUE.equals(hasSymptoms)) {
                log.info("Promoting symptomatic survey subject to SUSPECT");
                healthStatusService.updateStatus(anonymousId, "SUSPECT");
            }
        } catch (Exception e) {
            log.error("Failed to process survey event ({})", e.getClass().getSimpleName());
        }
    }

    @KafkaListener(topics = "certificate.validated", groupId = "promotion-service-group")
    public void onCertificateValidated(Map<String, Object> event) {
        log.info("Received certificate validation event");
        
        try {
            String anonymousId = (String) event.get("anonymousId");
            String status = (String) event.get("status");
            
            if (anonymousId != null && "APPROVED".equals(status)) {
                log.info("Restoring approved certificate subject to ACTIVE");
                healthStatusService.updateStatus(anonymousId, "ACTIVE");
            }
        } catch (Exception e) {
            log.error("Failed to process certificate validation event ({})", e.getClass().getSimpleName());
        }
    }
}
