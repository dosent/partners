package com.simbirsoft.partners.localization.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Публикация конкретной версии черновика")
public record PublishRegistrationContentRequest(
        @NotNull @PositiveOrZero Long expectedDraftVersion) { }
