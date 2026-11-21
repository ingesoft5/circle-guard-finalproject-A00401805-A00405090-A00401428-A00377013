/**
 * CG-DOC: Unit tests for the promotion encounter business meter (DCG-340).
 */
package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.service.AutoCircleService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the encounter report endpoint publishes the shared business metric
 * {@code circleguard_promotion_encounters_total} with the {@code result} tag.
 * The Prometheus exposition is asserted too, because that is exactly what the
 * monitoring stack scrapes from {@code /actuator/prometheus}.
 */
class EncounterMetricsTest {

    private static final String METRIC = "circleguard.promotion.encounters";
    private static final String EXPOSITION_NAME = "circleguard_promotion_encounters_total";
    private static final String BODY = "{\"sourceId\":\"a\",\"targetId\":\"b\",\"locationId\":\"A-101\"}";

    private UserNodeRepository userRepository;
    private AutoCircleService autoCircleService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserNodeRepository.class);
        autoCircleService = mock(AutoCircleService.class);
    }

    private MockMvc mockMvcFor(MeterRegistry meterRegistry) {
        return MockMvcBuilders.standaloneSetup(
                new EncounterController(userRepository, autoCircleService, meterRegistry)
        ).build();
    }

    private double count(SimpleMeterRegistry registry, String result) {
        // Micrometer registers a meter lazily on first increment, so an absent series
        // means "never incremented" rather than a failure.
        io.micrometer.core.instrument.Counter counter = registry.find(METRIC).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void reportEncounter(MockMvc mockMvc) throws Exception {
        mockMvc.perform(post("/api/v1/encounters/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void reportEncounter_successIncrementsSuccessCounterOnly() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        reportEncounter(mockMvcFor(registry));

        assertEquals(1.0, count(registry, "success"), 0.0001);
        assertEquals(0.0, count(registry, "failure"), 0.0001);
    }

    @Test
    void reportEncounter_repositoryFailureIncrementsFailureCounterOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        doThrow(new IllegalStateException("neo4j unavailable"))
                .when(userRepository).recordEncounter(anyString(), anyString(), anyLong(), anyString());

        assertThrows(Exception.class, () -> reportEncounter(mockMvcFor(registry)));

        assertEquals(1.0, count(registry, "failure"), 0.0001);
        assertEquals(0.0, count(registry, "success"), 0.0001);
    }

    @Test
    void reportEncounter_publishesSharedPrometheusExposition() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        reportEncounter(mockMvcFor(registry));

        String scrape = registry.scrape();
        assertTrue(scrape.contains(EXPOSITION_NAME),
                "Prometheus exposition must contain " + EXPOSITION_NAME + " but was:\n" + scrape);
        assertTrue(scrape.contains("result=\"success\""),
                "Exposition must carry result=\"success\" but was:\n" + scrape);
    }
}
