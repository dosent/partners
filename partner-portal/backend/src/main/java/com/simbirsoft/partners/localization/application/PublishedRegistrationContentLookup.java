package com.simbirsoft.partners.localization.application;

import java.time.Instant;
import java.util.Map;
import com.simbirsoft.partners.localization.domain.OfferType;
import java.util.Optional;

/** Контракт для чтения неизменяемого опубликованного набора контента регистрации. */
public interface PublishedRegistrationContentLookup {
    Optional<PublishedRegistrationContent> findPublished(String locale);

    record PublishedRegistrationContent(
            String locale,
            int contentVersion,
            Map<String, String> strings,
            Map<OfferType, PublishedDocument> offers,
            PublishedDocument privacyNotice,
            String logoAssetKey,
            String contentSha256,
            Instant publishedAt) {
        public PublishedRegistrationContent {
            strings = Map.copyOf(strings);
            offers = Map.copyOf(offers);
        }
    }

    record PublishedDocument(String version, String title, String body, String sha256) { }
}
