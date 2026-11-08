/**
 * CG-DOC: Unit or integration tests for Jwt Token Service Test in the auth service.
 */
package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenServiceTest {

    private static final String SECRET = "my-super-secret-test-key-32-chars-long";

    @Test
    void shouldGenerateSignedJwtWithAnonymousIdSubjectAndPermissionsClaim() {
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 3600000L);
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "staff_guard",
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                        new SimpleGrantedAuthority("gate:scan")
                )
        );

        String token = jwtTokenService.generateToken(anonymousId, auth);
        assertNotNull(token);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);
        assertEquals(List.of("ROLE_GATE_STAFF", "gate:scan"), permissions);
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }
}
