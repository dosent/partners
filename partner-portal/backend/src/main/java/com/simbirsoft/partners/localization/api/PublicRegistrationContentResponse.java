package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.application.PublishedRegistrationContentLookup.PublishedRegistrationContent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Опубликованный набор локализованной регистрации")
public record PublicRegistrationContentResponse(
        String locale,
        int contentVersion,
        Map<String, String> strings,
        String offerVersion,
        String offerTitle,
        String offerBody,
        String privacyNoticeVersion,
        String privacyNoticeTitle,
        String privacyNoticeBody,
        String logoAssetKey,
        String contentSha256,
        Instant publishedAt) {
    public static PublicRegistrationContentResponse from(PublishedRegistrationContent content) {
        return new PublicRegistrationContentResponse(content.locale(), content.contentVersion(), content.strings(),
                content.offerVersion(), content.offerTitle(), content.offerBody(), content.privacyNoticeVersion(),
                content.privacyNoticeTitle(), content.privacyNoticeBody(), content.logoAssetKey(),
                content.contentSha256(), content.publishedAt());
    }
}
