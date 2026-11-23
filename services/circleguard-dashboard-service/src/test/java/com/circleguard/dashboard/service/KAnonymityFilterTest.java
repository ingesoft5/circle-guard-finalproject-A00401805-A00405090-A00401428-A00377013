/** CG-DOC: Tests for the dashboard k-anonymity filter. */
package com.circleguard.dashboard.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KAnonymityFilterTest {
    private final KAnonymityFilter filter = new KAnonymityFilter();

    @Test
    void shouldMaskPopulationBelowThresholdAndKeepMetadata() {
        Map<String, Object> raw = new LinkedHashMap<>(Map.of(
                "department", "Engineering", "timestamp", "2026-11-17",
                "totalUsers", 4, "suspectCount", 1));

        Map<String, Object> result = filter.apply(raw);

        assertEquals("<5", result.get("totalUsers"));
        assertEquals("Engineering", result.get("department"));
        assertEquals("2026-11-17", result.get("timestamp"));
        assertEquals("Insufficient data for privacy", result.get("note"));
        assertFalse(result.containsKey("suspectCount"));
        assertEquals(1, raw.get("suspectCount"));
    }

    @Test
    void shouldMaskOnlyPositiveCountsBelowThreshold() {
        Map<String, Object> raw = new LinkedHashMap<>(Map.of(
                "totalUsers", 12, "suspectCount", 4,
                "activeCount", 5, "confirmedCount", 6, "emptyCount", 0));

        Map<String, Object> result = filter.apply(raw);

        assertEquals("<5", result.get("suspectCount"));
        assertEquals(5, result.get("activeCount"));
        assertEquals(6, result.get("confirmedCount"));
        assertEquals(0, result.get("emptyCount"));
        assertEquals(4, raw.get("suspectCount"));
    }

    @Test
    void shouldPreservePopulationAtAndAboveThreshold() {
        assertEquals(5, filter.apply(Map.of("totalUsers", 5)).get("totalUsers"));
        assertEquals(6, filter.apply(Map.of("totalUsers", 6)).get("totalUsers"));
    }

    @Test
    void shouldAllowCustomThresholdAndHandleNull() {
        assertEquals("<3", filter.apply(Map.of("suspectCount", 2), 3).get("suspectCount"));
        assertTrue(filter.apply(null).isEmpty());
    }
}
