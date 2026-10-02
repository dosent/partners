package com.simbirsoft.partners.localization.domain;

import java.util.Map;
import java.util.EnumMap;

/** Содержимое редактируемого набора локали; неполные наборы допустимы до публикации. */
public record RegistrationContentDraft(
        Map<String, String> strings,
        Map<OfferType, OfferDocument> offers,
        String privacyNoticeVersion,
        String privacyNoticeTitle,
        String privacyNoticeBody,
        String logoAssetKey) {
    public RegistrationContentDraft {
        strings = strings == null ? Map.of() : Map.copyOf(strings);
        Map<OfferType, OfferDocument> normalizedOffers = new EnumMap<>(OfferType.class);
        if (offers != null) offers.forEach((type, offer) -> {
            if (type != null && offer != null) normalizedOffers.put(type, offer);
        });
        offers = Map.copyOf(normalizedOffers);
    }
}
