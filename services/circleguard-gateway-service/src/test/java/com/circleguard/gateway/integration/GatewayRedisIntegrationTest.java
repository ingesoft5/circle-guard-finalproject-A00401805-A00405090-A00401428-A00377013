/**
 * CG-DOC: Integration tests for gateway QR validation against a real Redis container (GW-CI-02).
 */
package com.circleguard.gateway.integration;

import com.circleguard.gateway.service.QrValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@Tag("integration")
@ActiveProfiles("test")
class GatewayRedisIntegrationTest extends AbstractRedisIntegrationTest {

    @Autowired
    private QrValidationService service;

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RECOVERED"})
    void allowsHealthyStatuses(String status) {
        String id = UUID.randomUUID().toString();
        seedStatus(id, status);

        QrValidationService.ValidationResult result = service.validateToken(token(id, 60_000));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
        assertEquals("Welcome to Campus", result.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPECT", "CONTAGIED", "PROBABLE", "CONFIRMED", "POTENTIAL"})
    void deniesRiskStatuses(String status) {
        String id = UUID.randomUUID().toString();
        seedStatus(id, status);

        QrValidationService.ValidationResult result = service.validateToken(token(id, 60_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    @Test
    void deniesWhenStatusIsMissing() {
        String id = UUID.randomUUID().toString();

        QrValidationService.ValidationResult result = service.validateToken(token(id, 60_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    @Test
    void deniesExpiredToken() {
        String id = UUID.randomUUID().toString();
        seedStatus(id, "ACTIVE");

        QrValidationService.ValidationResult result = service.validateToken(token(id, -1_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    private String token(String subject, long ttlMillis) {
        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
