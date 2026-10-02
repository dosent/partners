package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.OfferDocument;
import com.simbirsoft.partners.localization.domain.OfferType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Тексты, документы и бренд одной локали регистрации")
public record RegistrationContentValueResponse(
        Map<String, String> strings,
        Map<OfferType, OfferDocument> offers,
        String privacyNoticeVersion,
        String privacyNoticeTitle,
        String privacyNoticeBody,
        String logoAssetKey) {
    public static RegistrationContentValueResponse from(RegistrationContentDraft content) {
        return new RegistrationContentValueResponse(content.strings(), content.offers(),
                content.privacyNoticeVersion(), content.privacyNoticeTitle(),
                content.privacyNoticeBody(), content.logoAssetKey());
    }
}
