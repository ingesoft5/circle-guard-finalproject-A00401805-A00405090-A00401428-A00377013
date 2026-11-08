package com.circleguard.auth.controller;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import com.circleguard.auth.security.DualChainAuthenticationProvider;
import com.circleguard.auth.security.JwtAuthenticationFilter;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, DualChainAuthenticationProvider.class})
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalUserRepository localUserRepository;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void shouldReturnUsersWithGivenPermissionAndHandleNullEmail() throws Exception {
        LocalUser userWithEmail = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("health_user")
                .email("health@circleguard.edu")
                .isActive(true)
                .build();

        LocalUser userWithoutEmail = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("backup_nurse")
                .email(null)
                .isActive(true)
                .build();

        Mockito.when(localUserRepository.findUsersByPermissionName("alert:receive_priority"))
                .thenReturn(List.of(userWithEmail, userWithoutEmail));

        mockMvc.perform(get("/api/v1/users/permissions/alert:receive_priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("health_user"))
                .andExpect(jsonPath("$[0].email").value("health@circleguard.edu"))
                .andExpect(jsonPath("$[1].username").value("backup_nurse"))
                .andExpect(jsonPath("$[1].email").value(""));
    }
}
