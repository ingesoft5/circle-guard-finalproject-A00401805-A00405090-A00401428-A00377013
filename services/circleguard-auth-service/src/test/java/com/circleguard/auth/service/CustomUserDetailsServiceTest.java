package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

    @Mock
    private LocalUserRepository userRepository;

    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(userRepository);
    }

    @Test
    void shouldLoadActiveUserWithRolesAndGranularPermissions() {
        Permission p1 = Permission.builder().id(UUID.randomUUID()).name("identity:lookup").build();
        Permission p2 = Permission.builder().id(UUID.randomUUID()).name("symptom:report").build();
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name("HEALTH_CENTER")
                .permissions(Set.of(p1, p2))
                .build();
        LocalUser user = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("health_user")
                .password("$2b$12$hashedpassword")
                .email("health@circleguard.edu")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        Mockito.when(userRepository.findByUsername("health_user")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("health_user");

        assertEquals("health_user", userDetails.getUsername());
        assertEquals("$2b$12$hashedpassword", userDetails.getPassword());
        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_HEALTH_CENTER", "identity:lookup", "symptom:report"), authorities);
    }

    @Test
    void shouldThrowDisabledExceptionWhenUserIsInactive() {
        LocalUser inactiveUser = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("disabled_user")
                .password("hash")
                .isActive(false)
                .roles(Set.of())
                .build();

        Mockito.when(userRepository.findByUsername("disabled_user")).thenReturn(Optional.of(inactiveUser));

        assertThrows(DisabledException.class, () -> customUserDetailsService.loadUserByUsername("disabled_user"));
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserDoesNotExist() {
        Mockito.when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername("nonexistent"));
    }
}
