package com.circleguard.gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QrValidationService {
    private static final String STATUS_KEY_PREFIX = "user:status:";
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "RECOVERED");
    private static final Set<String> DENIED_STATUSES =
            Set.of("SUSPECT", "PROBABLE", "CONFIRMED", "CONTAGIED", "POTENTIAL");

    private final StringRedisTemplate redisTemplate;

    @Value("${qr.secret}")
    private String qrSecret;

    public ValidationResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            return invalidToken();
        }

        Claims claims;
        try {
            Key key = Keys.hmacShaKeyFor(qrSecret.getBytes(StandardCharsets.UTF_8));
            claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return invalidToken();
        }

        String anonymousId = claims.getSubject();
        if (anonymousId == null || anonymousId.isBlank()) {
            return invalidToken();
        }

        String status = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);

        if (status == null || DENIED_STATUSES.contains(status) || !ALLOWED_STATUSES.contains(status)) {
            return new ValidationResult(false, "RED", "Access Denied: Health Risk Detected");
        }

        return new ValidationResult(true, "GREEN", "Welcome to Campus");
    }

    private ValidationResult invalidToken() {
        return new ValidationResult(false, "RED", "Invalid or Expired Token");
    }

    public record ValidationResult(boolean valid, String status, String message) {}
}
