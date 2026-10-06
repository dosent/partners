package com.simbirsoft.partners.access.domain;

import java.time.Instant;
import java.util.UUID;

public record PartnerApiKey(UUID id, String provider, String subject, String name, String prefix,
        byte[] secretHash, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
    public PartnerApiKey {
        secretHash = secretHash.clone();
    }
    @Override public byte[] secretHash() { return secretHash.clone(); }

    public PartnerApiKey usedAt(Instant instant) {
        return new PartnerApiKey(id, provider, subject, name, prefix, secretHash, createdAt, instant, revokedAt);
    }
    public PartnerApiKey revokedAt(Instant instant) {
        return new PartnerApiKey(id, provider, subject, name, prefix, secretHash, createdAt, lastUsedAt, instant);
    }
}
