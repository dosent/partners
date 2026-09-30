package com.simbirsoft.partners.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Единый формат ошибки API")
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String traceId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
