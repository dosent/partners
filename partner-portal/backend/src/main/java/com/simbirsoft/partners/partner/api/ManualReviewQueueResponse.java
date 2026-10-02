package com.simbirsoft.partners.partner.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Безопасная очередь заявок на ручную проверку без реквизитов")
public record ManualReviewQueueResponse(
        List<Item> items,
        int page,
        int size,
        long total) {
    @Schema(description = "Краткая строка очереди, не содержит реквизитов партнёра")
    public record Item(UUID id, Instant submittedAt, int revisionNumber) { }
}
