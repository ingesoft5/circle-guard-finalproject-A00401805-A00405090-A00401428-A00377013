/**
 * CG-DOC: Unit or integration tests for Identity Vault Controller Test in the identity service.
 */
package com.circleguard.identity.controller;

import com.circleguard.identity.config.SecurityConfig;
import com.circleguard.identity.service.IdentityVaultService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityVaultController.class)
@Import(SecurityConfig.class)
class IdentityVaultControllerTest {

    private static final String JWT_SECRET = "test-jwt-secret-32-chars-long-12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityVaultService vaultService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @WithMockUser(authorities = "identity:lookup")
    void lookupIdentity_WithPermission_ReturnsRealIdentity() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realIdentity").value("user@example.com"));

        // Verify Kafka event was emitted
        verify(kafkaTemplate).send(eq("audit.identity.accessed"), any());
    }

    @Test
    void lookupIdentity_WithValidJwt_ReturnsRealIdentity() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId)
                        .header("Authorization", "Bearer " + token("anonymous-1", List.of("identity:lookup"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realIdentity").value("user@example.com"));

        verify(kafkaTemplate).send(eq("audit.identity.accessed"), any());
    }

    @Test
    void lookupIdentity_WithInvalidJwt_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/identities/lookup/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "other:permission")
    void lookupIdentity_WithoutPermission_Returns403() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookupIdentity_Unauthenticated_Returns401() throws Exception {
        UUID anonymousId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "identity:lookup")
    void lookupIdentity_NotFound_Returns404ProblemDetail() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found"));

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Identity not found"));

        // Verify Kafka event was emitted even on failure
        verify(kafkaTemplate).send(eq("audit.identity.accessed"), any());
    }

    @Test
    void mapIdentity_ReturnsAnonymousId() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("user@example.com")).thenReturn(anonymousId);

        mockMvc.perform(post("/api/v1/identities/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realIdentity\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }

    @Test
    void registerVisitor_ReturnsAnonymousId() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("VISITOR|visitor@example.com|Visitor|Meeting"))
                .thenReturn(anonymousId);

        mockMvc.perform(post("/api/v1/identities/visitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Visitor\",\"email\":\"visitor@example.com\","
                                + "\"reason_for_visit\":\"Meeting\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }

    private String token(String subject, List<String> permissions) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(subject)
                .claim("permissions", permissions)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
