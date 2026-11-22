/**
 * CG-DOC: business service for the gateway service: Qr Validation Service.
 */
package com.circleguard.gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QrValidationService {
    private static final String STATUS_KEY_PREFIX = "user:status:";
    // Metric name required by HU-GATEWAY-06 acceptance criteria.
    private static final String QR_METRIC = "circleguard_gateway_qr_validations_total";
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "RECOVERED");
    private static final Set<String> DENIED_STATUSES =
            Set.of("SUSPECT", "PROBABLE", "CONFIRMED", "CONTAGIED", "POTENTIAL");

    private final StringRedisTemplate redisTemplate;
    // Injected by Spring; used to increment counters with the "result" tag.
    private final MeterRegistry meterRegistry;

    @Value("${qr.secret}")
    private String qrSecret;

    public ValidationResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            // Missing token is treated as an invalid input, not a health denial.
            return record(invalidToken(), "invalid");
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
            // Signature/expiration/format errors are counted as invalid tokens.
            return record(invalidToken(), "invalid");
        }

        String anonymousId = claims.getSubject();
        if (anonymousId == null || anonymousId.isBlank()) {
            return record(invalidToken(), "invalid");
        }

        String status = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);

        if (status == null || DENIED_STATUSES.contains(status) || !ALLOWED_STATUSES.contains(status)) {
            // Known risk status, unknown status, or missing Redis status => denied.
            return record(new ValidationResult(false, "RED", "Access Denied: Health Risk Detected"), "denied");
        }

        // Only explicitly allowed statuses reach this point.
        return record(new ValidationResult(true, "GREEN", "Welcome to Campus"), "success");
    }

    private ValidationResult record(ValidationResult result, String outcome) {
        // The "result" tag allows Prometheus/Grafana to split success/denied/invalid.
        meterRegistry.counter(QR_METRIC, "result", outcome).increment();
        return result;
    }

    private ValidationResult invalidToken() {
        return new ValidationResult(false, "RED", "Invalid or Expired Token");
    }

    public record ValidationResult(boolean valid, String status, String message) {}
}
