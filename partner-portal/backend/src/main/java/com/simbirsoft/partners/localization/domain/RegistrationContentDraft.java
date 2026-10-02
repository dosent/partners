package com.simbirsoft.partners.localization.domain;

import java.util.Map;

/** Содержимое редактируемого набора локали; неполные наборы допустимы до публикации. */
public record RegistrationContentDraft(
        Map<String, String> strings,
        String offerVersion,
        String offerTitle,
        String offerBody,
        String privacyNoticeVersion,
        String privacyNoticeTitle,
        String privacyNoticeBody,
        String logoAssetKey) {
    public RegistrationContentDraft {
        strings = strings == null ? Map.of() : Map.copyOf(strings);
    }
}
