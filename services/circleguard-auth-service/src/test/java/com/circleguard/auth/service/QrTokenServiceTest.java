package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QrTokenServiceTest {

    private static final String QR_SECRET = "my-super-secret-qr-test-key-32-chars-long";

    @Test
    void shouldGenerateShortLivedQrTokenWithAnonymousIdSubject() {
        QrTokenService qrTokenService = new QrTokenService(QR_SECRET, 60000L);
        UUID anonymousId = UUID.randomUUID();

        String qrToken = qrTokenService.generateQrToken(anonymousId);
        assertNotNull(qrToken);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(QR_SECRET.getBytes()))
                .build()
                .parseClaimsJws(qrToken)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(60000L, ttlMs);
    }
}
