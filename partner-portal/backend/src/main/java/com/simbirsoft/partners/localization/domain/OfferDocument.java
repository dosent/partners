package com.simbirsoft.partners.localization.domain;

import jakarta.validation.constraints.Size;

/** Редактируемый простой текст оферты; поля могут быть пустыми до публикации. */
public record OfferDocument(
        @Size(max = 100) String version,
        @Size(max = 1000) String title,
        @Size(max = 500000) String body) { }
