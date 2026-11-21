/**
 * CG-DOC: HTTP controller for the auth service: Login Controller.
 */
package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.service.JwtTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    private static final String LOGIN_ATTEMPTS_METRIC = "circleguard_auth_login_attempts_total";
    private static final String VISITOR_HANDOFF_METRIC = "circleguard_auth_visitor_handoff_total";

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtService;
    private final IdentityClient identityClient;
    private final MeterRegistry meterRegistry;

    @Autowired
    public LoginController(
            AuthenticationManager authManager,
            JwtTokenService jwtService,
            IdentityClient identityClient,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(authManager, jwtService, identityClient,
                meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new) : new SimpleMeterRegistry());
    }

    public LoginController(
            AuthenticationManager authManager,
            JwtTokenService jwtService,
            IdentityClient identityClient,
            MeterRegistry meterRegistry) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.identityClient = identityClient;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        log.info("Authentication attempt initiated for username={}", username);

        try {
            // 1. Authenticate (Dual-Chain)
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            log.info("Authentication succeeded for username={}", username);

            // 2. Anonymize (Fetch/Create Anonymous ID from Identity Service)
            UUID anonymousId = identityClient.getAnonymousId(username);
            log.debug("Anonymous identifier resolved for login session: anonymousId={}", anonymousId);

            // 3. Issue Token
            String token = jwtService.generateToken(anonymousId, auth);
            meterRegistry.counter(LOGIN_ATTEMPTS_METRIC, "result", "success").increment();

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "anonymousId", anonymousId.toString()
            ));
        } catch (AuthenticationException e) {
            meterRegistry.counter(LOGIN_ATTEMPTS_METRIC, "result", "failure").increment();
            log.warn("Authentication failed for username={}: {}", username, e.getMessage());
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        } catch (Exception e) {
            meterRegistry.counter(LOGIN_ATTEMPTS_METRIC, "result", "error").increment();
            log.error("Unexpected error during login flow for username={}: {}", username, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/visitor/handoff")
    public ResponseEntity<Map<String, String>> generateVisitorHandoff(@RequestBody Map<String, String> request) {
        String anonymousIdStr = request.get("anonymousId");
        if (anonymousIdStr == null) {
            log.warn("Rejected visitor handoff request with missing anonymousId");
            return ResponseEntity.badRequest().build();
        }

        UUID anonymousId = UUID.fromString(anonymousIdStr);

        // Create a dummy authentication for the visitor
        Authentication visitorAuth = new UsernamePasswordAuthenticationToken(
                anonymousIdStr,
                null,
                List.of(new SimpleGrantedAuthority("VISITOR"))
        );

        String token = jwtService.generateToken(anonymousId, visitorAuth);
        meterRegistry.counter(VISITOR_HANDOFF_METRIC).increment();
        log.info("Generated visitor handoff token for anonymousId={}", anonymousId);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "handoffPayload", "HANDOFF_TOKEN:" + anonymousId.toString() + ":" + token
        ));
    }
}
