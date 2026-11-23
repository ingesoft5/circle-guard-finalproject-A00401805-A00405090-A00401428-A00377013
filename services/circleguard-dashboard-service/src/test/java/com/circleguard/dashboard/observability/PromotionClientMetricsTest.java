/**
 * CG-DOC: promotion-service call metrics and latency tests for the dashboard service.
 */
package com.circleguard.dashboard.observability;

import com.circleguard.dashboard.client.PromotionClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionClientMetricsTest {

    private static final String CALLS = "circleguard.dashboard.promotion.calls";
    private static final String DURATION = "circleguard.dashboard.promotion.call.duration";

    private final PromotionClient client = new PromotionClient();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "promotionServiceUrl", "http://promotion:8088");
        client.setMeterRegistry(registry);
    }

    @Test
    void recordsSuccessfulAndFailedPromotionCallsWithBoundedTags() {
        when(restTemplate.getForObject("http://promotion:8088/api/v1/health-status/stats", Map.class))
                .thenReturn(Map.of("totalGreen", 12))
                .thenThrow(new ResourceAccessException("connection refused"));

        Map<String, Object> successful = client.getHealthStats();
        Map<String, Object> failed = client.getHealthStats();

        assertEquals(12, successful.get("totalGreen"));
        assertEquals("Service unavailable", failed.get("error"));
        assertEquals(1.0, registry.get(CALLS).tag("operation", "healthStats")
                .tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get(CALLS).tag("operation", "healthStats")
                .tag("outcome", "error").counter().count());
        assertEquals(1L, registry.get(DURATION).tag("operation", "healthStats")
                .tag("outcome", "success").timer().count());
        assertEquals(1L, registry.get(DURATION).tag("operation", "healthStats")
                .tag("outcome", "error").timer().count());
        assertEquals(2, registry.get(CALLS).tag("operation", "healthStats").counter().getId().getTags().size(),
                "Department names and URLs must not become metric tag values");
    }

    @Test
    void recordsDepartmentCallOutcomeWithoutDepartmentTag() {
        when(restTemplate.getForObject(
                "http://promotion:8088/api/v1/health-status/stats/department/Engineering", Map.class))
                .thenThrow(new ResourceAccessException("connection refused"));

        Map<String, Object> result = client.getHealthStatsByDepartment("Engineering");

        assertEquals("Engineering", result.get("department"));
        assertEquals(1.0, registry.get(CALLS).tag("operation", "departmentStats")
                .tag("outcome", "error").counter().count());
        assertTrue(registry.get(CALLS).tag("operation", "departmentStats").counters()
                .stream().noneMatch(meter -> meter.getId().getTag("department") != null));
    }
}
