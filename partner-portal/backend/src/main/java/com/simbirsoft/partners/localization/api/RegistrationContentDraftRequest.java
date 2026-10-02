package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.application.RegistrationContentService.DraftCommand;
import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.OfferDocument;
import com.simbirsoft.partners.localization.domain.OfferType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Редактируемый набор текстов регистрации")
public record RegistrationContentDraftRequest(
        @NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedDraftVersion,
        @Size(max = 100) Map<@NotNull String, @NotNull @Size(max = 12000) String> strings,
        Map<@NotNull OfferType, @Valid OfferDocument> offers,
        @Size(max = 100) String privacyNoticeVersion,
        @Size(max = 1000) String privacyNoticeTitle,
        @Size(max = 500000) String privacyNoticeBody,
        @Size(max = 32) String logoAssetKey) {
    public DraftCommand toCommand() {
        return new DraftCommand(expectedDraftVersion, new RegistrationContentDraft(strings, offers,
                privacyNoticeVersion, privacyNoticeTitle, privacyNoticeBody, logoAssetKey));
    }
}
