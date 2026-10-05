package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.PartnerApiKeyService.KeySummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Безопасное представление API-ключа партнёра")
public record PartnerApiKeyResponse(UUID id, String name, String prefix, Instant createdAt,
        Instant lastUsedAt, Instant revokedAt) {
    public static PartnerApiKeyResponse from(KeySummary value) {
        return new PartnerApiKeyResponse(value.id(), value.name(), value.prefix(), value.createdAt(), value.lastUsedAt(), value.revokedAt());
    }
}
