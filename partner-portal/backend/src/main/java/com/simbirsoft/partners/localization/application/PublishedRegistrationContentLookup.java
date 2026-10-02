package com.simbirsoft.partners.localization.application;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Контракт для чтения неизменяемого опубликованного набора контента регистрации. */
public interface PublishedRegistrationContentLookup {
    Optional<PublishedRegistrationContent> findPublished(String locale);

    record PublishedRegistrationContent(
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
        public PublishedRegistrationContent {
            strings = Map.copyOf(strings);
        }
    }
}
