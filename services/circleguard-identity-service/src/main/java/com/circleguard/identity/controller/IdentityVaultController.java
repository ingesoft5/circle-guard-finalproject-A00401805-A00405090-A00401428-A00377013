/**
 * CG-DOC: HTTP controller for the identity service: Identity Vault Controller.
 */
package com.circleguard.identity.controller;

import com.circleguard.identity.event.IdentityAccessEvent;
import com.circleguard.identity.service.IdentityAuditPublisher;
import com.circleguard.identity.service.IdentityVaultService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identities")
public class IdentityVaultController {
    private static final Logger log = LoggerFactory.getLogger(IdentityVaultController.class);
    private static final String MAPPINGS_METRIC = "circleguard_identity_mappings_total";
    private static final String LOOKUPS_METRIC = "circleguard_identity_lookups_total";

    private final IdentityVaultService vaultService;
    private final IdentityAuditPublisher auditPublisher;
    private final MeterRegistry meterRegistry;

    @Autowired
    public IdentityVaultController(
            IdentityVaultService vaultService,
            IdentityAuditPublisher auditPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(vaultService, auditPublisher,
                meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new) : new SimpleMeterRegistry());
    }

    public IdentityVaultController(
            IdentityVaultService vaultService,
            IdentityAuditPublisher auditPublisher,
            MeterRegistry meterRegistry) {
        this.vaultService = vaultService;
        this.auditPublisher = auditPublisher;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    /**
     * Maps a real identity to an anonymous ID.
     * Usually called during onboarding/auth.
     */
    @PostMapping("/map")
    public ResponseEntity<Map<String, UUID>> mapIdentity(@RequestBody Map<String, String> request) {
        String realIdentity = request.get("realIdentity");
        UUID anonymousId = vaultService.getOrCreateAnonymousId(realIdentity);
        meterRegistry.counter(MAPPINGS_METRIC, "type", "user").increment();
        log.debug("Mapped institutional identity to anonymousId={}", anonymousId);
        return ResponseEntity.ok(Map.of("anonymousId", anonymousId));
    }

    /**
     * Registers a temporary visitor and maps their details to an Anonymous ID.
     */
    @PostMapping("/visitor")
    public ResponseEntity<Map<String, UUID>> registerVisitor(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = request.get("email");
        String reason = request.get("reason_for_visit");

        // Combine details into a single identity string for the vault
        String realIdentity = "VISITOR|" + email + "|" + name + "|" + reason;
        UUID anonymousId = vaultService.getOrCreateAnonymousId(realIdentity);
        meterRegistry.counter(MAPPINGS_METRIC, "type", "visitor").increment();
        log.info("Registered visitor identity mapping: anonymousId={}", anonymousId);

        return ResponseEntity.ok(Map.of("anonymousId", anonymousId));
    }

    /**
     * Restricted lookup of real identity from anonymous ID.
     * Authorized for Health Center personnel only.
     */
    @GetMapping("/lookup/{id}")
    @PreAuthorize("hasAuthority('identity:lookup')")
    public ResponseEntity<Map<String, String>> lookupIdentity(@PathVariable UUID id) {
        String requestingUser = getCurrentUser();
        String status = "SUCCESS";

        try {
            String realIdentity = vaultService.resolveRealIdentity(id);
            meterRegistry.counter(LOOKUPS_METRIC, "status", "success").increment();
            log.info("Authorized vault lookup succeeded: anonymousId={}, requester={}", id, requestingUser);
            return ResponseEntity.ok(Map.of("realIdentity", realIdentity));
        } catch (ResponseStatusException e) {
            status = "FAILURE_" + e.getStatusCode().toString();
            meterRegistry.counter(LOOKUPS_METRIC, "status", "not_found").increment();
            log.warn("Authorized vault lookup returned {}: anonymousId={}, requester={}", e.getStatusCode(), id, requestingUser);
            throw e;
        } catch (Exception e) {
            status = "ERROR";
            meterRegistry.counter(LOOKUPS_METRIC, "status", "error").increment();
            log.error("Unexpected error during vault lookup: anonymousId={}, requester={}: {}", id, requestingUser, e.getMessage());
            throw e;
        } finally {
            emitAuditEvent(id, requestingUser, status);
        }
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private void emitAuditEvent(UUID anonymousId, String user, String status) {
        IdentityAccessEvent event = IdentityAccessEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("audit.identity.accessed")
                .timestamp(Instant.now())
                .source("circleguard-identity-service")
                .payload(IdentityAccessEvent.IdentityAccessPayload.builder()
                        .anonymousId(anonymousId)
                        .requestingUser(user)
                        .accessStatus(status)
                        .build())
                .metadata(IdentityAccessEvent.IdentityAccessMetadata.builder()
                        .correlationId(UUID.randomUUID().toString())
                        .version(1)
                        .build())
                .build();

        auditPublisher.publishAuditEvent(event);
    }
}
