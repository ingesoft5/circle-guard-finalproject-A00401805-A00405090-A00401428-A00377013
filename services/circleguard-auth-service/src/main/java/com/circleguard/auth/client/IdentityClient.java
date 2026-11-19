/**
 * CG-DOC: integration client for the auth service: Identity Client.
 */
package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class IdentityClient {
    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);

    private final RestTemplate restTemplate;
    private final String identityUrl;
    private final boolean fallbackEnabled;

    @Autowired
    public IdentityClient(
            @Value("${identity.service.url:http://localhost:8083/api/v1/identities/map}") String identityUrl,
            @Value("${identity.service.fallback-enabled:false}") boolean fallbackEnabled) {
        this(new RestTemplate(), identityUrl, fallbackEnabled);
    }

    public IdentityClient(String identityUrl) {
        this(new RestTemplate(), identityUrl, false);
    }

    public IdentityClient(RestTemplate restTemplate, String identityUrl) {
        this(restTemplate, identityUrl, false);
    }

    public IdentityClient(RestTemplate restTemplate, String identityUrl, boolean fallbackEnabled) {
        this.restTemplate = restTemplate;
        this.identityUrl = identityUrl;
        this.fallbackEnabled = fallbackEnabled;
    }

    @CircuitBreaker(name = "identityClient", fallbackMethod = "fallbackAnonymousId")
    @Retry(name = "identityClient")
    public UUID getAnonymousId(String realIdentity) {
        try {
            Map<String, String> request = Map.of("realIdentity", realIdentity);
            Map<?, ?> response = restTemplate.postForObject(identityUrl, request, Map.class);
            return UUID.fromString(response.get("anonymousId").toString());
        } catch (RuntimeException ex) {
            if (fallbackEnabled) {
                return fallbackAnonymousId(realIdentity, ex);
            }
            throw ex;
        }
    }

    public UUID fallbackAnonymousId(String realIdentity, Throwable ex) {
        log.warn("Identity service circuit breaker / fallback activated (fallbackEnabled={}): {}",
                fallbackEnabled, ex != null ? ex.getMessage() : "unknown error");
        if (fallbackEnabled) {
            return UUID.nameUUIDFromBytes(
                    ("circleguard:" + realIdentity).getBytes(StandardCharsets.UTF_8));
        }
        if (ex instanceof RuntimeException runtimeEx) {
            throw runtimeEx;
        }
        throw new RuntimeException("Identity service unavailable", ex);
    }
}
