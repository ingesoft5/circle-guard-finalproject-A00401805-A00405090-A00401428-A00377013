/**
 * CG-DOC: integration client for the auth service: Identity Client.
 */
package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Component
public class IdentityClient {
    // In a real microservice, this would use Feign or WebClient
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

    public UUID getAnonymousId(String realIdentity) {
        try {
            Map<String, String> request = Map.of("realIdentity", realIdentity);
            Map response = restTemplate.postForObject(identityUrl, request, Map.class);
            return UUID.fromString(response.get("anonymousId").toString());
        } catch (RuntimeException ex) {
            if (fallbackEnabled) {
                return UUID.nameUUIDFromBytes(
                        ("circleguard:" + realIdentity).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            throw ex;
        }
    }
}
