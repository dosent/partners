package com.simbirsoft.partners.access.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerApiKeyService {
    private final PartnerApiKeyStore store;
    private final SecureRandom random = new SecureRandom();

    public PartnerApiKeyService(PartnerApiKeyStore store) { this.store = store; }

    @Transactional
    public CreatedKey create(PartnerPrincipal owner, String name) {
        if (owner == null) throw new IllegalArgumentException("Partner is required");
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) throw new IllegalArgumentException("Invalid key name");
        UUID id = UUID.randomUUID();
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        String secret = "ppk_" + id + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        Instant now = Instant.now();
        store.save(new PartnerApiKeyStore.Credential(id, owner.provider(), owner.subject(), normalizedName,
                "ppk_" + id, hash(secret), now, null, null));
        return new CreatedKey(id, normalizedName, "ppk_" + id, secret, now);
    }

    @Transactional(readOnly = true)
    public List<KeySummary> list(PartnerPrincipal owner) {
        return store.findByOwner(owner.provider(), owner.subject()).stream().map(PartnerApiKeyService::summary).toList();
    }

    @Transactional
    public void revoke(PartnerPrincipal owner, UUID keyId) {
        var current = store.findById(keyId).filter(value -> value.provider().equals(owner.provider())
                && value.subject().equals(owner.subject())).orElseThrow(() -> new IllegalArgumentException("Key not found"));
        if (current.revokedAt() == null) store.update(new PartnerApiKeyStore.Credential(current.id(), current.provider(),
                current.subject(), current.name(), current.prefix(), current.secretHash(), current.createdAt(),
                current.lastUsedAt(), Instant.now()));
    }

    @Transactional
    public Optional<ApiKeyIdentity> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("ppk_")) return Optional.empty();
        int separator = rawKey.indexOf('_', 4);
        if (separator < 0) return Optional.empty();
        UUID id;
        try { id = UUID.fromString(rawKey.substring(4, separator)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
        var credential = store.findById(id).orElse(null);
        if (credential == null || credential.revokedAt() != null
                || !MessageDigest.isEqual(credential.secretHash(), hash(rawKey))) return Optional.empty();
        store.update(new PartnerApiKeyStore.Credential(credential.id(), credential.provider(), credential.subject(),
                credential.name(), credential.prefix(), credential.secretHash(), credential.createdAt(), Instant.now(), null));
        return Optional.of(new ApiKeyIdentity(id, credential.provider(), credential.subject()));
    }

    private static byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static KeySummary summary(PartnerApiKeyStore.Credential value) {
        return new KeySummary(value.id(), value.name(), value.prefix(), value.createdAt(), value.lastUsedAt(), value.revokedAt());
    }
    public record CreatedKey(UUID id, String name, String prefix, String secret, Instant createdAt) { }
    public record KeySummary(UUID id, String name, String prefix, Instant createdAt, Instant lastUsedAt, Instant revokedAt) { }
    public record ApiKeyIdentity(UUID id, String provider, String subject) { }
}
