/**
 * CG-DOC: Unit or integration tests for Identity Vault Controller Test in the identity service.
 */
package com.circleguard.identity.controller;

import com.circleguard.identity.config.SecurityConfig;
import com.circleguard.identity.event.IdentityAccessEvent;
import com.circleguard.identity.service.IdentityAuditPublisher;
import com.circleguard.identity.service.IdentityVaultService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    @MockitoBean
    private IdentityVaultService vaultService;

    @MockitoBean
    private IdentityAuditPublisher auditPublisher;

    @Test
    @WithMockUser(authorities = "identity:lookup")
    void lookupIdentity_WithPermission_ReturnsRealIdentity() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realIdentity").value("user@example.com"));

        // Verify resilient audit publisher was invoked
        verify(auditPublisher).publishAuditEvent(any(IdentityAccessEvent.class));
    }

    @Test
    void lookupIdentity_WithValidJwt_ReturnsRealIdentity() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId)
                        .header("Authorization", "Bearer " + token("anonymous-1", List.of("identity:lookup"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realIdentity").value("user@example.com"));

        verify(auditPublisher).publishAuditEvent(any(IdentityAccessEvent.class));
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
                .andExpect(jsonPath("$.instance").value("/api/v1/identities/lookup/" + anonymousId))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Identity not found"));

        // Verify resilient audit publisher was invoked even on failure
        verify(auditPublisher).publishAuditEvent(any(IdentityAccessEvent.class));
    }

    @Test
    void shouldRecordMicrometerCountersAcrossMappingAndLookupOperations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdentityVaultController controller = new IdentityVaultController(vaultService, auditPublisher, registry);

        UUID id = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("user@example.com")).thenReturn(id);
        when(vaultService.getOrCreateAnonymousId("VISITOR|v@example.com|V|Visit")).thenReturn(id);
        when(vaultService.resolveRealIdentity(id)).thenReturn("user@example.com");

        controller.mapIdentity(Map.of("realIdentity", "user@example.com"));
        controller.registerVisitor(Map.of("name", "V", "email", "v@example.com", "reason_for_visit", "Visit"));
        controller.lookupIdentity(id);

        assertEquals(1.0, registry.counter("circleguard_identity_mappings_total", "type", "user").count());
        assertEquals(1.0, registry.counter("circleguard_identity_mappings_total", "type", "visitor").count());
        assertEquals(1.0, registry.counter("circleguard_identity_lookups_total", "status", "success").count());
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
