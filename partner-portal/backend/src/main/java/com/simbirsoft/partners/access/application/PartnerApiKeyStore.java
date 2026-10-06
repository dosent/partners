package com.simbirsoft.partners.access.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerApiKeyStore {
    void save(Credential credential);
    Optional<Credential> findById(UUID id);
    List<Credential> findByOwner(String provider, String subject);
    void update(Credential credential);
    boolean markUsedIfActive(UUID id, Instant usedAt);

    record Credential(UUID id, String provider, String subject, String name, String prefix,
            byte[] secretHash, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
        public Credential { secretHash = secretHash.clone(); }
        @Override public byte[] secretHash() { return secretHash.clone(); }
    }
}
