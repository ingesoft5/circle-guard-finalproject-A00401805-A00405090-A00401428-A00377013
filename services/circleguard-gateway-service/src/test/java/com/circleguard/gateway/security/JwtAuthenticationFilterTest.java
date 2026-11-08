/**
 * CG-DOC: Unit or integration tests for Jwt Authentication Filter Test in the gateway service.
 */
package com.circleguard.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Key;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private final String secret = "my-super-secret-test-key-32-chars-long";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateTokenWithPermissions() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(secret);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token(List.of("gate:scan")));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("gate:scan")));
    }

    @Test
    void shouldNotAuthenticateInvalidToken() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(secret);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private String token(List<String> permissions) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setClaims(Map.of("permissions", permissions))
                .setSubject("anonymous-id")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
