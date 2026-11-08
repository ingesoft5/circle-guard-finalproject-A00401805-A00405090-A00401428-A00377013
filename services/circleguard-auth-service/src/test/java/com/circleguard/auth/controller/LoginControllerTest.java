/**
 * CG-DOC: Unit or integration tests for Login Controller Test in the auth service.
 */
package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
public class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authManager;

    @MockBean
    private JwtTokenService jwtService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void shouldLoginSuccessfullyAndReturnAnonymizedToken() throws Exception {
        String username = "testuser";
        String password = "password123";
        UUID anonymousId = UUID.randomUUID();
        String token = "mock-jwt-token";

        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(authManager.authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        Mockito.when(identityClient.getAnonymousId(username)).thenReturn(anonymousId);

        Mockito.when(jwtService.generateToken(Mockito.eq(anonymousId), Mockito.any(Authentication.class)))
                .thenReturn(token);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    void shouldReturn401WhenCredentialsAreInvalid() throws Exception {
        Mockito.when(authManager.authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"wrongpass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void shouldReturn500WhenIdentityClientFails() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(authManager.authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        Mockito.when(identityClient.getAnonymousId("testuser"))
                .thenThrow(new RuntimeException("Identity service unavailable"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"password123\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error: Identity service unavailable"));
    }

    @Test
    void shouldGenerateVisitorHandoffSuccessfully() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        String visitorToken = "visitor-jwt-token";

        Mockito.when(jwtService.generateToken(Mockito.eq(anonymousId), Mockito.any(Authentication.class)))
                .thenReturn(visitorToken);

        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"" + anonymousId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(visitorToken))
                .andExpect(jsonPath("$.handoffPayload").value("HANDOFF_TOKEN:" + anonymousId + ":" + visitorToken));
    }

    @Test
    void shouldReturn400WhenVisitorHandoffMissingAnonymousId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
