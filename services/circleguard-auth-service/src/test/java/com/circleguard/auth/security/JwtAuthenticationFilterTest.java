package com.circleguard.auth.security;

import com.circleguard.auth.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class JwtAuthenticationFilterTest {

    private static final String SECRET = "my-super-secret-test-key-32-chars-long";
    private JwtAuthenticationFilter filter;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(SECRET);
        jwtTokenService = new JwtTokenService(SECRET, 3600000L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPopulateSecurityContextWhenValidBearerTokenProvided() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Authentication sourceAuth = new UsernamePasswordAuthenticationToken(
                "health_user",
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_HEALTH_CENTER"),
                        new SimpleGrantedAuthority("identity:lookup")
                )
        );
        String jwt = jwtTokenService.generateToken(anonymousId, sourceAuth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        Authentication contextAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(contextAuth);
        assertEquals(anonymousId.toString(), contextAuth.getName());
        Set<String> authorities = contextAuth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_HEALTH_CENTER"));
        assertTrue(authorities.contains("identity:lookup"));
        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearSecurityContextAndContinueWhenTokenIsInvalid() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null, List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsMissingOrNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        Mockito.verify(filterChain).doFilter(request, response);
    }
}
