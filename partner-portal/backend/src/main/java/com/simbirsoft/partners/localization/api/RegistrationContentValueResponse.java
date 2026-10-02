package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Тексты, документы и бренд одной локали регистрации")
public record RegistrationContentValueResponse(
        Map<String, String> strings,
        String offerVersion,
        String offerTitle,
        String offerBody,
        String privacyNoticeVersion,
        String privacyNoticeTitle,
        String privacyNoticeBody,
        String logoAssetKey) {
    public static RegistrationContentValueResponse from(RegistrationContentDraft content) {
        return new RegistrationContentValueResponse(content.strings(), content.offerVersion(), content.offerTitle(),
                content.offerBody(), content.privacyNoticeVersion(), content.privacyNoticeTitle(),
                content.privacyNoticeBody(), content.logoAssetKey());
    }
}
