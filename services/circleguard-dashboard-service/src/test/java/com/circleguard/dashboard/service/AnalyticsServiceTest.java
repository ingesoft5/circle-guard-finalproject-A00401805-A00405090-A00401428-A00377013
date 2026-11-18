package com.circleguard.dashboard.service;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private PromotionClient promotionClient;
    @Mock private KAnonymityFilter filter;
    @InjectMocks private AnalyticsService service;

    @Test
    void shouldDelegateCampusAndGlobalSummaries() {
        Map<String, Object> stats = Map.of("totalGreen", 12);
        when(promotionClient.getHealthStats()).thenReturn(stats);

        assertSame(stats, service.getCampusSummary());
        assertSame(stats, service.getGlobalHealthStats());
        verify(promotionClient, times(2)).getHealthStats();
    }

    @Test
    void shouldFilterDepartmentStats() {
        Map<String, Object> raw = Map.of("totalUsers", 4);
        Map<String, Object> masked = Map.of("totalUsers", "<5");
        when(promotionClient.getHealthStatsByDepartment("Engineering")).thenReturn(raw);
        when(filter.apply(raw)).thenReturn(masked);

        assertSame(masked, service.getDepartmentStats("Engineering"));
        verify(filter).apply(raw);
    }

    @Test
    void shouldPreservePromotionFailureResponse() {
        Map<String, Object> failure = Map.of("error", "Service unavailable", "department", "Engineering");
        when(promotionClient.getHealthStatsByDepartment("Engineering")).thenReturn(failure);
        when(filter.apply(failure)).thenReturn(failure);

        assertSame(failure, service.getDepartmentStats("Engineering"));
    }

    @Test
    void shouldMaskSmallEntryCountsAndKeepLargerCounts() {
        UUID locationId = UUID.randomUUID();
        Map<String, Object> small = new HashMap<>(Map.of("entry_count", 4L));
        Map<String, Object> enough = new HashMap<>(Map.of("entry_count", 5L));
        List<Map<String, Object>> rows = new ArrayList<>(List.of(small, enough));
        when(jdbc.queryForList(contains("FROM entry_logs"), eq(locationId))).thenReturn(rows);

        assertSame(rows, service.getEntryTrends(locationId));
        assertEquals("<5", small.get("entry_count"));
        assertEquals("Insufficient data for privacy", small.get("note"));
        assertEquals(5L, enough.get("entry_count"));
        assertFalse(enough.containsKey("note"));
    }

    @Test
    void shouldReturnHourlyAndDailySeriesFromDatabase() {
        List<Map<String, Object>> hourly = List.of(Map.of("status", "ACTIVE", "total", 10L));
        List<Map<String, Object>> daily = List.of(Map.of("status", "SUSPECT", "total", 2L));
        when(jdbc.queryForList(contains("date_trunc('hour'"), eq(3))).thenReturn(hourly);
        when(jdbc.queryForList(contains("date_trunc('day'"), eq(7))).thenReturn(daily);

        assertSame(hourly, service.getTimeSeries("hourly", 3));
        assertSame(daily, service.getTimeSeries("daily", 7));
    }

    @Test
    void shouldReturnBoundedFallbackSeriesWhenDatabaseQueryFails() {
        when(jdbc.queryForList(anyString(), eq(2))).thenThrow(new IllegalStateException("status_events missing"));

        List<Map<String, Object>> rows = service.getTimeSeries("hourly", 2);

        assertEquals(8, rows.size());
        assertEquals(List.of("ACTIVE", "SUSPECT", "PROBABLE", "CONFIRMED"),
                rows.subList(0, 4).stream().map(row -> row.get("status")).toList());
        assertTrue(rows.stream().allMatch(row -> row.containsKey("bucket") && row.containsKey("total")));
    }
}
