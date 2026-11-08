/**
 * CG-DOC: Unit or integration tests for Dual Chain Authentication Provider Test in the auth service.
 */
package com.circleguard.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DualChainAuthenticationProviderTest {

    @Mock
    private LdapAuthenticationProvider ldapProvider;

    @Mock
    private DaoAuthenticationProvider localProvider;

    private DualChainAuthenticationProvider dualChainProvider;

    @BeforeEach
    void setUp() {
        dualChainProvider = new DualChainAuthenticationProvider(ldapProvider, localProvider);
    }

    @Test
    void shouldAuthenticateWithLdapWhenPrimarySucceeds() {
        Authentication token = new UsernamePasswordAuthenticationToken("student1", "secret");
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                "student1", "secret", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        Mockito.when(ldapProvider.authenticate(token)).thenReturn(authenticated);

        Authentication result = dualChainProvider.authenticate(token);

        assertSame(authenticated, result);
        Mockito.verify(ldapProvider).authenticate(token);
        Mockito.verifyNoInteractions(localProvider);
    }

    @Test
    void shouldFallbackToLocalDatabaseWhenLdapThrowsAuthenticationException() {
        Authentication token = new UsernamePasswordAuthenticationToken("staff_guard", "password");
        Authentication localAuthenticated = new UsernamePasswordAuthenticationToken(
                "staff_guard", "password", List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF"))
        );

        Mockito.when(ldapProvider.authenticate(token))
                .thenThrow(new BadCredentialsException("LDAP authentication failed"));
        Mockito.when(localProvider.authenticate(token)).thenReturn(localAuthenticated);

        Authentication result = dualChainProvider.authenticate(token);

        assertSame(localAuthenticated, result);
        Mockito.verify(ldapProvider).authenticate(token);
        Mockito.verify(localProvider).authenticate(token);
    }

    @Test
    void shouldThrowExceptionWhenBothProvidersFail() {
        Authentication token = new UsernamePasswordAuthenticationToken("unknown", "wrong");

        Mockito.when(ldapProvider.authenticate(token))
                .thenThrow(new BadCredentialsException("LDAP failed"));
        Mockito.when(localProvider.authenticate(token))
                .thenThrow(new BadCredentialsException("Local DB failed"));

        assertThrows(BadCredentialsException.class, () -> dualChainProvider.authenticate(token));
    }

    @Test
    void shouldSupportUsernamePasswordAuthenticationToken() {
        assertTrue(dualChainProvider.supports(UsernamePasswordAuthenticationToken.class));
        assertFalse(dualChainProvider.supports(AnonymousAuthenticationToken.class));
    }
}
