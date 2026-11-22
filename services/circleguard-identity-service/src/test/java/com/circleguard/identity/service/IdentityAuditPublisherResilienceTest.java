/**
 * CG-DOC: Spring AOP Resilience4j tests for IdentityAuditPublisher (@CircuitBreaker, @Retry and Prometheus metrics).
 */
package com.circleguard.identity.service;

import com.circleguard.identity.event.IdentityAccessEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityAuditPublisherResilienceTest {

    @Autowired
    private IdentityAuditPublisher auditPublisher;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("identityAuditKafka");
        circuitBreaker.reset();
        reset(kafkaTemplate);
    }

    @Test
    void shouldLoadExternalizedResilience4jConfigAndProxyBean() {
        assertTrue(AopUtils.isAopProxy(auditPublisher), "IdentityAuditPublisher must be a Spring AOP proxy");
        assertEquals(10, circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(5, circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        assertEquals(50.0f, circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());

        Retry retry = retryRegistry.retry("identityAuditKafka");
        assertEquals(3, retry.getRetryConfig().getMaxAttempts());
    }

    @Test
    void shouldRetryTransientKafkaFailuresAndSucceedOnThirdAttemptViaAop() {
        IdentityAccessEvent event = sampleEvent();
        when(kafkaTemplate.send(eq(IdentityAuditPublisher.AUDIT_TOPIC), any()))
                .thenThrow(new RuntimeException("Kafka broker temporarily unavailable"))
                .thenThrow(new RuntimeException("Kafka broker temporarily unavailable"))
                .thenReturn(CompletableFuture.completedFuture(null));

        auditPublisher.publishAuditEvent(event);

        verify(kafkaTemplate, times(3)).send(eq(IdentityAuditPublisher.AUDIT_TOPIC), eq(event));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void shouldOpenCircuitBreakerAfterFiveFailedCallsAndExposePrometheusMetric() throws Exception {
        IdentityAccessEvent event = sampleEvent();
        when(kafkaTemplate.send(eq(IdentityAuditPublisher.AUDIT_TOPIC), any()))
                .thenThrow(new RuntimeException("Kafka cluster down"));

        // 5 failed calls * 3 retry attempts = 15 send attempts -> circuit opens
        for (int i = 0; i < 5; i++) {
            auditPublisher.publishAuditEvent(event);
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        verify(kafkaTemplate, times(15)).send(eq(IdentityAuditPublisher.AUDIT_TOPIC), eq(event));

        // 6th call must short-circuit to fallback without invoking KafkaTemplate
        auditPublisher.publishAuditEvent(event);
        verify(kafkaTemplate, times(15)).send(eq(IdentityAuditPublisher.AUDIT_TOPIC), eq(event));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(
                        "resilience4j_circuitbreaker_state{name=\"identityAuditKafka\",state=\"open\",} 1.0")))
                .andExpect(content().string(Matchers.containsString(
                        "circleguard_identity_audit_fallback_total")));
    }

    private IdentityAccessEvent sampleEvent() {
        return IdentityAccessEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("audit.identity.accessed")
                .timestamp(Instant.now())
                .source("circleguard-identity-service")
                .payload(IdentityAccessEvent.IdentityAccessPayload.builder()
                        .anonymousId(UUID.randomUUID())
                        .requestingUser("health_user")
                        .accessStatus("SUCCESS")
                        .build())
                .build();
    }
}
