package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.application.PublishedRegistrationContentLookup.PublishedRegistrationContent;
import com.simbirsoft.partners.localization.application.PublishedRegistrationContentLookup.PublishedDocument;
import com.simbirsoft.partners.localization.domain.OfferType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Опубликованный набор локализованной регистрации")
public record PublicRegistrationContentResponse(
        String locale,
        int contentVersion,
        Map<String, String> strings,
        Map<OfferType, PublishedDocument> offers,
        PublishedDocument privacyNotice,
        String logoAssetKey,
        String contentSha256,
        Instant publishedAt) {
    public static PublicRegistrationContentResponse from(PublishedRegistrationContent content) {
        return new PublicRegistrationContentResponse(content.locale(), content.contentVersion(), content.strings(),
                content.offers(), content.privacyNotice(), content.logoAssetKey(),
                content.contentSha256(), content.publishedAt());
    }
}
