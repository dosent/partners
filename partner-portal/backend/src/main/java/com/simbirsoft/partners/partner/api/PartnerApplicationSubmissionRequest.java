package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.partner.application.PartnerApplicationSubmission;
import com.simbirsoft.partners.partner.domain.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Данные новой редакции заявки и подтверждения опубликованных документов")
public record PartnerApplicationSubmissionRequest(
        @NotNull PartnerType partnerType,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String phone,
        @NotBlank @Size(max = 8) String countryCode,
        @NotBlank @Size(max = 100) String region,
        @Size(max = 160) String companyName,
        @Size(max = 64) String taxIdentifier,
        @NotBlank @Size(max = 8) String locale,
        @Positive int contentVersion,
        @NotBlank @Size(max = 100) String offerVersion,
        boolean offerAccepted,
        @NotBlank @Size(max = 100) String privacyNoticeVersion,
        boolean privacyAccepted) {

    @AssertTrue(message = "Offer acceptance is required")
    @Schema(description = "Должно быть true после принятия оферты")
    public boolean isOfferAccepted() {
        return offerAccepted;
    }

    @AssertTrue(message = "Personal data processing consent is required")
    @Schema(description = "Должно быть true после принятия согласия на обработку данных")
    public boolean isPrivacyAccepted() {
        return privacyAccepted;
    }

    public PartnerApplicationSubmission toCommand() {
        return new PartnerApplicationSubmission(partnerType, fullName, email, phone, countryCode, region,
                companyName, taxIdentifier, locale, contentVersion, offerVersion, offerAccepted,
                privacyNoticeVersion, privacyAccepted);
    }
}
