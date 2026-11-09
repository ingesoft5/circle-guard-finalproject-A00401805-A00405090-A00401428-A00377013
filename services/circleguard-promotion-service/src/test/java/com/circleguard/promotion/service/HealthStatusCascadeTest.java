package com.circleguard.promotion.service;

import com.circleguard.promotion.exception.FenceException;
import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the status cascade: transitions, propagation, batching,
 * priority alerts, recovery and the mandatory fence window.
 */
class HealthStatusCascadeTest {

    private static final String TOPIC_STATUS = "promotion.status.changed";
    private static final String TOPIC_PRIORITY = "alert.priority";

    private UserNodeRepository userNodeRepository;
    private Neo4jClient neo4jClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private SystemSettingsRepository settingsRepository;
    private CircleNodeRepository circleNodeRepository;
    private HealthStatusService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userNodeRepository = mock(UserNodeRepository.class);
        neo4jClient = mock(Neo4jClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        settingsRepository = mock(SystemSettingsRepository.class);
        circleNodeRepository = mock(CircleNodeRepository.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(settingsRepository.getSettings()).thenReturn(Optional.of(SystemSettings.builder()
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build()));
        when(circleNodeRepository.findNewlyFencedCircles(anyString())).thenReturn(Collections.emptyList());

        service = new HealthStatusService(
                userNodeRepository, neo4jClient, redisTemplate, kafkaTemplate,
                settingsRepository, circleNodeRepository);
    }

    private Neo4jClient.UnboundRunnableSpec stubQuery(Map<String, Object> result) {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyLong()).to(anyString())
                .fetch().one()).thenReturn(Optional.of(result));
        return spec;
    }

    private Map<String, Object> resultWithContacts(List<Map<String, String>> contacts) {
        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", "user-1");
        result.put("affectedContacts", contacts);
        return result;
    }

    @Test
    void updateStatus_propagatesContactsToRedisAndKafka() {
        stubQuery(resultWithContacts(List.of(Map.of("id", "contact-1", "status", "SUSPECT"))));

        service.updateStatus("user-1", "SUSPECT");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(valueOperations).multiSet(captor.capture());
        assertEquals("SUSPECT", captor.getValue().get("user:status:user-1"));
        assertEquals("SUSPECT", captor.getValue().get("user:status:contact-1"));
        verify(kafkaTemplate).send(eq(TOPIC_STATUS), eq("user-1"), anyMap());
    }

    @Test
    void updateStatus_confirmedTriggersPriorityAlert() {
        stubQuery(resultWithContacts(List.of(Map.of("id", "contact-1", "status", "SUSPECT"))));

        service.updateStatus("user-1", "CONFIRMED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(TOPIC_PRIORITY), eq("user-1"), captor.capture());
        assertEquals("CONFIRMED_CASE", captor.getValue().get("eventType"));
    }

    @Test
    void updateStatus_largeOutbreakTriggersPriorityAlert() {
        List<Map<String, String>> contacts = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            contacts.add(Map.of("id", "contact-" + i, "status", "PROBABLE"));
        }
        stubQuery(resultWithContacts(contacts));

        service.updateStatus("user-1", "SUSPECT");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq(TOPIC_PRIORITY), eq("user-1"), captor.capture());
        assertEquals("LARGE_OUTBREAK", captor.getValue().get("eventType"));
    }

    @Test
    void updateStatus_batchesRedisWritesAboveTwoThousandEntries() {
        List<Map<String, String>> contacts = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            contacts.add(Map.of("id", "contact-" + i, "status", "PROBABLE"));
        }
        stubQuery(resultWithContacts(contacts));

        service.updateStatus("user-1", "SUSPECT");

        // 2002 entries total (source + contacts) => two batches (2000 + 2)
        verify(valueOperations, times(2)).multiSet(anyMap());
    }

    @Test
    void updateStatus_noResultDoesNotTouchRedis() {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyLong()).to(anyString())
                .fetch().one()).thenReturn(Optional.empty());

        service.updateStatus("ghost", "SUSPECT");

        verify(valueOperations, never()).multiSet(anyMap());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void updateStatus_broadcastsFencedCircles() {
        stubQuery(resultWithContacts(List.of()));
        when(circleNodeRepository.findNewlyFencedCircles("user-1"))
                .thenReturn(List.of(CircleNode.builder().id(9L).name("Familia").locationId("A-101").build()));

        service.updateStatus("user-1", "CONFIRMED");

        verify(kafkaTemplate).send(eq("circle.fenced"), eq("9"), anyMap());
    }

    @Test
    void updateStatusToActive_insideFenceWindowThrows() {
        long fiveDaysAgo = System.currentTimeMillis() - Duration.ofDays(5).toMillis();
        when(userNodeRepository.findById("user-1")).thenReturn(Optional.of(UserNode.builder()
                .anonymousId("user-1").status("SUSPECT").statusUpdatedAt(fiveDaysAgo).build()));

        assertThrows(FenceException.class, () -> service.updateStatus("user-1", "ACTIVE"));
    }

    @Test
    void updateStatusToActive_afterFenceWindowSucceeds() {
        long twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis();
        when(userNodeRepository.findById("user-1")).thenReturn(Optional.of(UserNode.builder()
                .anonymousId("user-1").status("SUSPECT").statusUpdatedAt(twentyDaysAgo).build()));
        stubQuery(resultWithContacts(List.of()));

        service.updateStatus("user-1", "ACTIVE");

        verify(kafkaTemplate).send(eq(TOPIC_STATUS), eq("user-1"), anyMap());
    }

    @Test
    void resolveStatus_releasesContactsAndBroadcasts() {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.bind(any()).to(anyString()).fetch().one())
                .thenReturn(Optional.of(Map.of("releasedIds", List.of("l1-a"))));

        service.resolveStatus("user-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(valueOperations).multiSet(captor.capture());
        assertEquals("ACTIVE", captor.getValue().get("user:status:user-1"));
        assertEquals("ACTIVE", captor.getValue().get("user:status:l1-a"));
        verify(kafkaTemplate).send(eq(TOPIC_STATUS), eq("user-1"), anyMap());
    }

    @Test
    void resolveStatus_insideFenceWindowThrowsWithoutOverride() {
        long twoDaysAgo = System.currentTimeMillis() - Duration.ofDays(2).toMillis();
        when(userNodeRepository.findById("user-1")).thenReturn(Optional.of(UserNode.builder()
                .anonymousId("user-1").status("PROBABLE").statusUpdatedAt(twoDaysAgo).build()));

        assertThrows(FenceException.class, () -> service.resolveStatus("user-1", false));
    }

    @Test
    void promoteToRecovered_setsStatusAndExpiresInThirtyDays() {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class,
                org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.bind(anyString()).to(anyString()).fetch().one())
                .thenReturn(Optional.of(Map.of("releasedIds", List.of())));

        service.promoteToRecovered("user-1");

        verify(valueOperations).set("user:status:user-1", "RECOVERED");
        verify(redisTemplate).expire(eq("user:status:user-1"), eq(Duration.ofDays(30)));
    }

    @Test
    void getCachedStatus_readsFromRedis() {
        when(valueOperations.get("user:status:user-1")).thenReturn("SUSPECT");

        assertEquals("SUSPECT", service.getCachedStatus("user-1"));
    }
}
