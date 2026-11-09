/**
 * CG-DOC: Unit or integration tests for Qr Validation Service Test in the gateway service.
 */
package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class QrValidationServiceTest {

    private QrValidationService service;
    private ValueOperations<String, String> valueOps;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RECOVERED"})
    void shouldAllowAllowedStatuses(String status) {
        String id = UUID.randomUUID().toString();
        when(valueOps.get("user:status:" + id)).thenReturn(status);

        QrValidationService.ValidationResult result = service.validateToken(token(id, 60_000));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPECT", "PROBABLE", "CONFIRMED", "CONTAGIED", "POTENTIAL", "UNKNOWN", "CLEAR"})
    void shouldDenyRiskOrUnknownStatuses(String status) {
        String id = UUID.randomUUID().toString();
        when(valueOps.get("user:status:" + id)).thenReturn(status);

        QrValidationService.ValidationResult result = service.validateToken(token(id, 60_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void shouldDenyWhenStatusIsMissing() {
        String id = UUID.randomUUID().toString();
        when(valueOps.get("user:status:" + id)).thenReturn(null);

        QrValidationService.ValidationResult result = service.validateToken(token(id, 60_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void shouldDenyExpiredToken() {
        String id = UUID.randomUUID().toString();
        QrValidationService.ValidationResult result = service.validateToken(token(id, -1_000));

        assertFalse(result.valid());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void shouldDenyMissingOrMalformedToken() {
        assertFalse(service.validateToken(null).valid());
        assertFalse(service.validateToken("").valid());
        assertFalse(service.validateToken("not-a-jwt").valid());
    }

    @Test
    void shouldDenyTokenWithInvalidSignature() {
        String id = UUID.randomUUID().toString();
        String foreignToken = token(id, 60_000, "another-secret-key-32-chars-long!!");

        QrValidationService.ValidationResult result = service.validateToken(foreignToken);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void shouldDenyTokenWithoutSubject() {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void shouldPropagateRedisFailure() {
        String id = UUID.randomUUID().toString();
        when(valueOps.get("user:status:" + id)).thenThrow(new RedisConnectionFailureException("down"));

        assertThrows(RedisConnectionFailureException.class, () -> service.validateToken(token(id, 60_000)));
    }

    private String token(String subject, long ttlMillis) {
        return token(subject, ttlMillis, secret);
    }

    private String token(String subject, long ttlMillis, String signingSecret) {
        Key key = Keys.hmacShaKeyFor(signingSecret.getBytes());
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
