/**
 * CG-DOC: Unit or integration tests for Analytics Controller Test in the dashboard service.
 */
package com.circleguard.dashboard.controller;

import com.circleguard.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.verify;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void shouldReturnHealthBoardStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGreen", 1500);
        stats.put("totalExposed", 45);

        Mockito.when(analyticsService.getGlobalHealthStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/health-board")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGreen").value(1500))
                .andExpect(jsonPath("$.totalExposed").value(45));
    }

    @Test
    void shouldReturnTrendsForLocation() throws Exception {
        UUID locationId = UUID.randomUUID();
        List<Map<String, Object>> trends = new ArrayList<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("hour", "08:00");
        entry.put("count", 120);
        trends.add(entry);

        Mockito.when(analyticsService.getEntryTrends(locationId)).thenReturn(trends);

        mockMvc.perform(get("/api/v1/analytics/trends/" + locationId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hour").value("08:00"))
                .andExpect(jsonPath("$[0].count").value(120));
    }

    @Test
    void shouldReturnCampusSummary() throws Exception {
        Mockito.when(analyticsService.getCampusSummary()).thenReturn(Map.of("totalGreen", 42));

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGreen").value(42));
        verify(analyticsService).getCampusSummary();
    }

    @Test
    void shouldReturnDepartmentStats() throws Exception {
        Mockito.when(analyticsService.getDepartmentStats("Engineering"))
                .thenReturn(Map.of("department", "Engineering", "suspectCount", "<5"));

        mockMvc.perform(get("/api/v1/analytics/department/Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Engineering"))
                .andExpect(jsonPath("$.suspectCount").value("<5"));
        verify(analyticsService).getDepartmentStats("Engineering");
    }

    @Test
    void shouldUseDefaultTimeSeriesParameters() throws Exception {
        Mockito.when(analyticsService.getTimeSeries("hourly", 24))
                .thenReturn(List.of(Map.of("status", "ACTIVE", "total", 10)));

        mockMvc.perform(get("/api/v1/analytics/time-series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(10));
        verify(analyticsService).getTimeSeries("hourly", 24);
    }

    @Test
    void shouldPassExplicitTimeSeriesParameters() throws Exception {
        Mockito.when(analyticsService.getTimeSeries("daily", 7)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/time-series")
                        .param("period", "daily")
                        .param("limit", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(analyticsService).getTimeSeries("daily", 7);
    }

    @Test
    void shouldReturnExistingErrorMapWhenPromotionFails() throws Exception {
        Mockito.when(analyticsService.getCampusSummary())
                .thenReturn(Map.of("error", "Service unavailable"));

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Service unavailable"));
    }
}
