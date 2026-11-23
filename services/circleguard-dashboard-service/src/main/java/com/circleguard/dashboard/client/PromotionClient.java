/**
 * CG-DOC: instrumented integration client for the dashboard service: Promotion Client.
 */
package com.circleguard.dashboard.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.Map;

@Component
@Slf4j
public class PromotionClient {

    private static final String CALLS_METRIC = "circleguard.dashboard.promotion.calls";
    private static final String DURATION_METRIC = "circleguard.dashboard.promotion.call.duration";
    private static final String OPERATION_HEALTH = "healthStats";
    private static final String OPERATION_DEPARTMENT = "departmentStats";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_ERROR = "error";

    private RestTemplate restTemplate = new RestTemplate();
    private MeterRegistry meterRegistry = Metrics.globalRegistry;

    @Value("${circleguard.promotion-service.url:http://localhost:8088}")
    private String promotionServiceUrl;

    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealthStats() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Map<String, Object> result = restTemplate.getForObject(
                    promotionServiceUrl + "/api/v1/health-status/stats",
                    Map.class
            );
            record(OPERATION_HEALTH, OUTCOME_SUCCESS, sample);
            return result;
        } catch (Exception e) {
            record(OPERATION_HEALTH, OUTCOME_ERROR, sample);
            log.error("Promotion health stats call failed operation={} outcome={} exception={}",
                    OPERATION_HEALTH, OUTCOME_ERROR, e.getClass().getSimpleName(), e);
            return Map.of("error", "Service unavailable", "timestamp", new Date());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealthStatsByDepartment(String department) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Map<String, Object> result = restTemplate.getForObject(
                    promotionServiceUrl + "/api/v1/health-status/stats/department/" + department,
                    Map.class
            );
            record(OPERATION_DEPARTMENT, OUTCOME_SUCCESS, sample);
            return result;
        } catch (Exception e) {
            record(OPERATION_DEPARTMENT, OUTCOME_ERROR, sample);
            log.error("Promotion department stats call failed operation={} outcome={} exception={}",
                    OPERATION_DEPARTMENT, OUTCOME_ERROR, e.getClass().getSimpleName(), e);
            return Map.of("error", "Service unavailable", "department", department, "timestamp", new Date());
        }
    }

    private void record(String operation, String outcome, Timer.Sample sample) {
        sample.stop(Timer.builder(DURATION_METRIC)
                .tags("operation", operation, "outcome", outcome)
                .description("Latency of dashboard calls to promotion-service.")
                .register(meterRegistry));
        meterRegistry.counter(CALLS_METRIC, "operation", operation, "outcome", outcome).increment();
    }
}
