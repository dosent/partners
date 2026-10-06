package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.PartnerApiKeyService.CreatedKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Новый ключ; secret возвращается только при создании")
public record CreatedPartnerApiKeyResponse(UUID id, String name, String prefix, String secret, Instant createdAt) {
    public static CreatedPartnerApiKeyResponse from(CreatedKey value) {
        return new CreatedPartnerApiKeyResponse(value.id(), value.name(), value.prefix(), value.secret(), value.createdAt());
    }
}
