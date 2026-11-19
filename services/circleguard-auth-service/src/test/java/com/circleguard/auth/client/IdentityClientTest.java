/**
 * CG-DOC: Unit or integration tests for Identity Client Test in the auth service.
 */
package com.circleguard.auth.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class IdentityClientTest {

    @Test
    void shouldMapRealIdentityToAnonymousUuidViaRestCall() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://identity-service:8083/api/v1/identities/map";
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        UUID expectedUuid = UUID.randomUUID();
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.realIdentity").value("student_01"))
                .andRespond(withSuccess(
                        "{\"anonymousId\": \"" + expectedUuid + "\"}",
                        MediaType.APPLICATION_JSON
                ));

        IdentityClient identityClient = new IdentityClient(restTemplate, url);
        UUID actualUuid = identityClient.getAnonymousId("student_01");

        assertEquals(expectedUuid, actualUuid);
        mockServer.verify();
    }

    @Test
    void shouldConstructWithDefaultRestTemplate() {
        IdentityClient client = new IdentityClient("http://localhost:8083/api/v1/identities/map");
        assertNotNull(client);
    }

    @Test
    void shouldFallbackToDeterministicUuidWhenFallbackEnabledAndRemoteFails() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://identity-service:8083/api/v1/identities/map";
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        IdentityClient identityClient = new IdentityClient(restTemplate, url, true);
        UUID fallbackUuid = identityClient.getAnonymousId("staff_guard");

        assertEquals(
                UUID.nameUUIDFromBytes("circleguard:staff_guard".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                fallbackUuid
        );
        mockServer.verify();
    }

    @Test
    void shouldThrowWhenFallbackDisabledAndRemoteFails() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://identity-service:8083/api/v1/identities/map";
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        IdentityClient identityClient = new IdentityClient(restTemplate, url, false);
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> identityClient.getAnonymousId("staff_guard")
        );
        mockServer.verify();
    }

    @Test
    void shouldWrapCheckedExceptionInFallbackWhenFallbackDisabled() {
        IdentityClient identityClient = new IdentityClient(new RestTemplate(), "http://localhost:8083/api/v1/identities/map", false);
        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> identityClient.fallbackAnonymousId("staff_guard", new Exception("Connection timeout"))
        );
        assertEquals("Identity service unavailable", thrown.getMessage());
    }

    @Test
    void shouldTransitionCircuitBreakerToOpenAndFallbackAfterConsecutiveFailures() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://identity-service:8083/api/v1/identities/map";
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        for (int i = 0; i < 3; i++) {
            mockServer.expect(requestTo(url))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        }

        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(3)
                        .minimumNumberOfCalls(3)
                        .failureRateThreshold(50.0f)
                        .build();
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.of(config).circuitBreaker("identityClient");

        IdentityClient remoteClient = new IdentityClient(restTemplate, url, false);
        IdentityClient fallbackClient = new IdentityClient(restTemplate, url, true);

        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        for (int i = 0; i < 3; i++) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> circuitBreaker.executeSupplier(() -> remoteClient.getAnonymousId("student_01"))
            );
        }

        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN, circuitBreaker.getState());

        UUID fallbackResult;
        try {
            fallbackResult = circuitBreaker.executeSupplier(() -> remoteClient.getAnonymousId("student_01"));
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
            fallbackResult = fallbackClient.fallbackAnonymousId("student_01", ex);
        }

        assertEquals(
                UUID.nameUUIDFromBytes("circleguard:student_01".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                fallbackResult
        );
        mockServer.verify();
    }
}
