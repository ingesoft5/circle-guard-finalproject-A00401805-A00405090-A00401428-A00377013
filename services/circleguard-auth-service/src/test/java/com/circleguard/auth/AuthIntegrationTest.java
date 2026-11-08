/**
 * CG-DOC: Unit or integration tests for Auth Integration Test in the auth service.
 */
package com.circleguard.auth;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LocalUserRepository localUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LdapAuthenticationProvider ldapAuthenticationProvider;

    @MockBean
    private IdentityClient identityClient;

    @BeforeEach
    void setUpDatabase() {
        Permission alertPerm = Permission.builder()
                .name("alert:receive_priority")
                .description("Receive priority alerts")
                .build();
        Permission lookupPerm = Permission.builder()
                .name("identity:lookup")
                .description("Lookup real identity")
                .build();
        entityManager.persist(alertPerm);
        entityManager.persist(lookupPerm);

        Role healthRole = Role.builder()
                .name("HEALTH_CENTER")
                .description("Health center staff")
                .permissions(Set.of(alertPerm, lookupPerm))
                .build();
        entityManager.persist(healthRole);

        LocalUser activeUser = LocalUser.builder()
                .username("health_user")
                .password(passwordEncoder.encode("password"))
                .email("health@circleguard.edu")
                .isActive(true)
                .roles(Set.of(healthRole))
                .build();
        localUserRepository.save(activeUser);
        entityManager.flush();

        // Simulate LDAP failure so DualChainAuthenticationProvider falls back to PostgreSQL/H2 local DB
        Mockito.when(ldapAuthenticationProvider.authenticate(Mockito.any()))
                .thenThrow(new BadCredentialsException("User not in LDAP directory"));
    }

    @Test
    void shouldCompleteFullDualChainLoginQrGenerationAndPermissionLookupFlow() throws Exception {
        UUID expectedAnonymousId = UUID.randomUUID();
        Mockito.when(identityClient.getAnonymousId("health_user")).thenReturn(expectedAnonymousId);

        // 1. Authenticate via POST /api/v1/auth/login (LDAP fails -> fallback to DB -> IdentityClient -> JWT)
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"health_user\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.anonymousId").value(expectedAnonymousId.toString()))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String jwtToken = loginJson.get("token").asText();
        assertFalse(jwtToken.isBlank());

        // 2. Use issued JWT to generate short-lived QR token via GET /api/v1/auth/qr/generate
        mockMvc.perform(get("/api/v1/auth/qr/generate")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value("60"));

        // 3. Query users with permission alert:receive_priority from relational DB
        mockMvc.perform(get("/api/v1/users/permissions/alert:receive_priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("health_user"))
                .andExpect(jsonPath("$[0].email").value("health@circleguard.edu"));
    }

    @Test
    void shouldRejectInvalidPasswordAgainstLocalDatabaseInFullFlow() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"health_user\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
