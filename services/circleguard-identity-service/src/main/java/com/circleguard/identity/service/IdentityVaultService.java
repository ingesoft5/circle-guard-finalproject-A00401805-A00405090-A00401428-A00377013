/**
 * CG-DOC: business service for the identity service: Identity Vault Service.
 */
package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class IdentityVaultService {
    private static final Logger log = LoggerFactory.getLogger(IdentityVaultService.class);

    private final IdentityMappingRepository repository;

    @Value("${vault.hash-salt}")
    private String hashSalt;

    /**
     * Maps a real identity to a secure, encrypted anonymous ID.
     */
    @Transactional
    public UUID getOrCreateAnonymousId(String realIdentity) {
        String hash = computeHash(realIdentity);

        return repository.findByIdentityHash(hash)
                .map(existing -> {
                    log.debug("Resolved existing anonymousId={} via blind index", existing.getAnonymousId());
                    return existing.getAnonymousId();
                })
                .orElseGet(() -> {
                    String salt = KeyGenerators.string().generateKey();
                    IdentityMapping mapping = IdentityMapping.builder()
                            .realIdentity(realIdentity)
                            .identityHash(hash)
                            .salt(salt)
                            .build();
                    UUID createdId = repository.save(mapping).getAnonymousId();
                    log.info("Created new encrypted identity vault mapping: anonymousId={}", createdId);
                    return createdId;
                });
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input + hashSalt).getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }

    public String resolveRealIdentity(UUID anonymousId) {
        return repository.findById(anonymousId)
                .map(IdentityMapping::getRealIdentity)
                .orElseThrow(() -> {
                    log.warn("Identity vault lookup failed: anonymousId={} not found", anonymousId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found");
                });
    }
}
