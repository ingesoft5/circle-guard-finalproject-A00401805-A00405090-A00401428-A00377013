/**
 * CG-DOC: Spring AOP Resilience4j tests for IdentityClient (@CircuitBreaker, @Retry and externalized config).
 */
package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "identity.service.fallback-enabled=true",
        "resilience4j.retry.instances.identityClient.wait-duration=10ms"
})
public class IdentityClientResilienceTest {

    private static final String IDENTITY_URL = "http://localhost:8083/api/v1/identities/map";

    @Autowired
    private IdentityClient identityClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private MockMvc mockMvc;

    private MockRestServiceServer mockServer;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("identityClient");
        circuitBreaker.reset();
        mockServer = MockRestServiceServer.createServer(identityClient.getRestTemplate());
    }

    @Test
    void shouldLoadExternalizedResilience4jConfigurationAndProxyBean() {
        assertTrue(AopUtils.isAopProxy(identityClient), "IdentityClient must be a Spring AOP proxy");
        assertEquals(10, circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(5, circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        assertEquals(50.0f, circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());

        Retry retry = retryRegistry.retry("identityClient");
        assertEquals(3, retry.getRetryConfig().getMaxAttempts());
    }

    @Test
    void shouldRetryTransientFailuresAndSucceedOnThirdAttemptViaAop() {
        UUID expectedUuid = UUID.randomUUID();

        mockServer.expect(requestTo(IDENTITY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        mockServer.expect(requestTo(IDENTITY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        mockServer.expect(requestTo(IDENTITY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"anonymousId\": \"" + expectedUuid + "\"}", MediaType.APPLICATION_JSON));

        UUID actualUuid = identityClient.getAnonymousId("student_transient");

        assertEquals(expectedUuid, actualUuid);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        mockServer.verify();
    }

    @Test
    void shouldOpenCircuitBreakerAfterMinimumCallsAndShortCircuitToFallbackMethod() throws Exception {
        UUID expectedFallback = UUID.nameUUIDFromBytes(
                "circleguard:student_fault".getBytes(StandardCharsets.UTF_8));

        // 5 failed calls (= minimum-number-of-calls: 5) * 3 retry attempts each = 15 HTTP attempts
        // before the circuit transitions from CLOSED to OPEN.
        mockServer.expect(ExpectedCount.times(15), requestTo(IDENTITY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        for (int i = 0; i < 5; i++) {
            UUID result = identityClient.getAnonymousId("student_fault");
            assertEquals(expectedFallback, result);
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 6th call must be short-circuited by OPEN CircuitBreaker without making any additional HTTP request
        UUID shortCircuitedResult = identityClient.getAnonymousId("student_fault");
        assertEquals(expectedFallback, shortCircuitedResult);

        // Verify /actuator/prometheus exports the exact metric expression for AuthIdentityCircuitBreakerOpen alert
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(
                        "resilience4j_circuitbreaker_state{name=\"identityClient\",state=\"open\"} 1.0")));

        mockServer.verify();
    }
}
