/**
 * CG-DOC: Unit tests for the identity vault mapping and lookup rules.
 */
package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityVaultServiceTest {

    @Mock
    private IdentityMappingRepository repository;

    @InjectMocks
    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "hashSalt", "test-salt");
    }

    @Test
    void getOrCreateAnonymousId_WhenMappingExists_ReturnsExistingId() {
        UUID existingId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(existingId)
                .realIdentity("user@example.com")
                .identityHash("existing-hash")
                .salt("salt")
                .build();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(mapping));

        UUID result = service.getOrCreateAnonymousId("user@example.com");

        assertEquals(existingId, result);
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateAnonymousId_WhenMappingDoesNotExist_CreatesEncryptedMapping() {
        UUID newId = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class))).thenAnswer(invocation -> {
            IdentityMapping mapping = invocation.getArgument(0);
            mapping.setAnonymousId(newId);
            return mapping;
        });

        UUID result = service.getOrCreateAnonymousId("new-user@example.com");

        assertEquals(newId, result);
        ArgumentCaptor<IdentityMapping> captor = ArgumentCaptor.forClass(IdentityMapping.class);
        verify(repository).save(captor.capture());
        IdentityMapping saved = captor.getValue();
        assertEquals("new-user@example.com", saved.getRealIdentity());
        assertEquals(64, saved.getIdentityHash().length());
        assertNotNull(saved.getSalt());
        assertFalse(saved.getSalt().isBlank());
    }

    @Test
    void resolveRealIdentity_WhenMappingExists_ReturnsPlainIdentity() {
        UUID anonymousId = UUID.randomUUID();
        when(repository.findById(anonymousId)).thenReturn(Optional.of(
                IdentityMapping.builder()
                        .anonymousId(anonymousId)
                        .realIdentity("user@example.com")
                        .identityHash("hash")
                        .salt("salt")
                        .build()));

        assertEquals("user@example.com", service.resolveRealIdentity(anonymousId));
    }

    @Test
    void resolveRealIdentity_WhenMappingDoesNotExist_ThrowsNotFound() {
        UUID anonymousId = UUID.randomUUID();
        when(repository.findById(anonymousId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.resolveRealIdentity(anonymousId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
