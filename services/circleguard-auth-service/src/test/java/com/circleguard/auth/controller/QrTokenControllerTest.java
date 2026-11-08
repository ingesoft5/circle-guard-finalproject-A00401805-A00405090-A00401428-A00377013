/**
 * CG-DOC: Unit or integration tests for Qr Token Controller Test in the auth service.
 */
package com.circleguard.auth.controller;

import com.circleguard.auth.security.DualChainAuthenticationProvider;
import com.circleguard.auth.security.JwtAuthenticationFilter;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.QrTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QrTokenController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, DualChainAuthenticationProvider.class, JwtTokenService.class})
public class QrTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private QrTokenService qrTokenService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void shouldGenerateQrTokenWhenAuthenticated() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        String expectedQrToken = "signed-qr-jwt-token";

        Mockito.when(qrTokenService.generateQrToken(anonymousId)).thenReturn(expectedQrToken);

        String bearerJwt = jwtTokenService.generateToken(
                anonymousId,
                new UsernamePasswordAuthenticationToken(
                        anonymousId.toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                )
        );

        mockMvc.perform(get("/api/v1/auth/qr/generate")
                .header("Authorization", "Bearer " + bearerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value(expectedQrToken))
                .andExpect(jsonPath("$.expiresIn").value("60"));
    }

    @Test
    void shouldReturn403WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().isForbidden());
    }
}
