package com.circleguard.dashboard.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromotionClientTest {
    private final PromotionClient client = new PromotionClient();
    private final RestTemplate restTemplate = mock(RestTemplate.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "promotionServiceUrl", "http://promotion:8088");
    }

    @Test
    void shouldFetchCampusAndDepartmentStats() {
        Map<String, Object> campus = Map.of("totalGreen", 12);
        Map<String, Object> department = Map.of("department", "Engineering", "totalUsers", 5);
        when(restTemplate.getForObject("http://promotion:8088/api/v1/health-status/stats", Map.class))
                .thenReturn(campus);
        when(restTemplate.getForObject(
                "http://promotion:8088/api/v1/health-status/stats/department/Engineering", Map.class))
                .thenReturn(department);

        assertSame(campus, client.getHealthStats());
        assertSame(department, client.getHealthStatsByDepartment("Engineering"));
    }

    @Test
    void shouldReturnErrorMapWhenPromotionIsUnavailable() {
        when(restTemplate.getForObject("http://promotion:8088/api/v1/health-status/stats", Map.class))
                .thenThrow(new ResourceAccessException("connection refused"));
        when(restTemplate.getForObject(
                "http://promotion:8088/api/v1/health-status/stats/department/Engineering", Map.class))
                .thenThrow(new ResourceAccessException("connection refused"));

        Map<String, Object> campus = client.getHealthStats();
        Map<String, Object> department = client.getHealthStatsByDepartment("Engineering");

        assertEquals("Service unavailable", campus.get("error"));
        assertTrue(campus.containsKey("timestamp"));
        assertEquals("Service unavailable", department.get("error"));
        assertEquals("Engineering", department.get("department"));
        assertTrue(department.containsKey("timestamp"));
    }
}
