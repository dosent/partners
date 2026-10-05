package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.PartnerApiKeyStore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_api_key")
public class PartnerApiKeyRecord {
    @Id private UUID id;
    @Column(name = "identity_provider", nullable = false, length = 32) private String provider;
    @Column(name = "external_subject", nullable = false, length = 255) private String subject;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 48) private String prefix;
    @Column(name = "secret_hash", nullable = false, columnDefinition = "bytea") private byte[] secretHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected PartnerApiKeyRecord() { }
    static PartnerApiKeyRecord from(PartnerApiKeyStore.Credential value) {
        var row = new PartnerApiKeyRecord(); row.id=value.id(); row.provider=value.provider(); row.subject=value.subject();
        row.name=value.name(); row.prefix=value.prefix(); row.secretHash=value.secretHash(); row.createdAt=value.createdAt();
        row.lastUsedAt=value.lastUsedAt(); row.revokedAt=value.revokedAt(); return row;
    }
    PartnerApiKeyStore.Credential toCredential() {
        return new PartnerApiKeyStore.Credential(id, provider, subject, name, prefix, secretHash, createdAt, lastUsedAt, revokedAt);
    }
    void update(PartnerApiKeyStore.Credential value) {
        if (!id.equals(value.id()) || !provider.equals(value.provider()) || !subject.equals(value.subject()))
            throw new IllegalArgumentException("Cannot change API key owner");
        lastUsedAt=value.lastUsedAt(); revokedAt=value.revokedAt();
    }
}
