package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.partner.application.PartnerProfileService.ProfileDetails;
import com.simbirsoft.partners.partner.domain.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Профиль партнёра текущей сессии")
public record PartnerProfileResponse(
        @Schema(description = "Идентификатор профиля") UUID id,
        @Schema(description = "Тип партнёра") PartnerType partnerType,
        @Schema(description = "ФИО из одобренной заявки") String fullName,
        @Schema(description = "Email из одобренной заявки") String email,
        @Schema(description = "Телефон из одобренной заявки") String phone,
        @Schema(description = "Код страны") String countryCode,
        @Schema(description = "Регион") String region,
        @Schema(description = "Название организации, если указано") String companyName,
        @Schema(description = "Время одобрения заявки") Instant approvedAt) {
    public static PartnerProfileResponse from(ProfileDetails details) {
        return new PartnerProfileResponse(details.id(), details.partnerType(), details.fullName(), details.email(),
                details.phone(), details.countryCode(), details.region(), details.companyName(), details.approvedAt());
    }
}
