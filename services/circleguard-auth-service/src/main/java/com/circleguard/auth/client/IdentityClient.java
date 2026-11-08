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

    @Autowired
    public IdentityClient(@Value("${identity.service.url:http://localhost:8083/api/v1/identities/map}") String identityUrl) {
        this(new RestTemplate(), identityUrl);
    }

    public IdentityClient(RestTemplate restTemplate, String identityUrl) {
        this.restTemplate = restTemplate;
        this.identityUrl = identityUrl;
    }

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map response = restTemplate.postForObject(identityUrl, request, Map.class);
        return UUID.fromString(response.get("anonymousId").toString());
    }
}
