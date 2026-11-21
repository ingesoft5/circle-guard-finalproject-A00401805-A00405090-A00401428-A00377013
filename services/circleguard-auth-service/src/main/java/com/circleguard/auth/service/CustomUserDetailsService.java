/**
 * CG-DOC: business service for the auth service: Custom User Details Service.
 */
package com.circleguard.auth.service;

import com.circleguard.auth.model.*;
import com.circleguard.auth.repository.LocalUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final LocalUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Looking up local user in database: username={}", username);
        LocalUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Local user not found in database: username={}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        log.debug("Local user found: username={}, active={}", user.getUsername(), user.getIsActive());

        if (!user.getIsActive()) {
            log.warn("Local user account is disabled: username={}", username);
            throw new DisabledException("User account is disabled");
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Add roles (prefixed with ROLE_)
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            
            // Add granular permissions
            role.getPermissions().forEach(permission -> {
                authorities.add(new SimpleGrantedAuthority(permission.getName()));
            });
        });

        return new User(
                user.getUsername(),
                user.getPassword(),
                authorities
        );
    }
}
