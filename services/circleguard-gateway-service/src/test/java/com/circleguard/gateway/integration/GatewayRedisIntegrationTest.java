/**
 * CG-DOC: Integration test for gateway QR validation against a real Redis container.
 */
package com.circleguard.gateway.integration;

import com.circleguard.gateway.service.QrValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class GatewayRedisIntegrationTest {

    private static final String SECRET = "my-super-secret-test-key-32-chars-long";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("qr.secret", () -> SECRET);
    }

    @Autowired
    private QrValidationService service;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void allowsActiveStatusFromRedis() {
        String id = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + id, "ACTIVE");

        QrValidationService.ValidationResult result = service.validateToken(token(id));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void deniesRiskStatusFromRedis() {
        String id = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + id, "SUSPECT");

        QrValidationService.ValidationResult result = service.validateToken(token(id));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void deniesWhenStatusIsMissing() {
        String id = UUID.randomUUID().toString();

        QrValidationService.ValidationResult result = service.validateToken(token(id));

        assertFalse(result.valid());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    private String token(String subject) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
