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
        @Schema(description = "Разрешённое контактное ФИО") String fullName,
        @Schema(description = "Разрешённый контактный email") String email,
        @Schema(description = "Разрешённый контактный телефон") String phone,
        @Schema(description = "Код страны") String countryCode,
        @Schema(description = "Регион") String region,
        @Schema(description = "Название организации, если указано") String companyName,
        @Schema(description = "Время одобрения заявки") Instant approvedAt,
        @Schema(description = "Локаль профиля: RU, KZ или EN") String locale,
        @Schema(description = "Включены email-уведомления") boolean emailNotificationsEnabled,
        @Schema(description = "Включены уведомления в кабинете") boolean inAppNotificationsEnabled) {
    public static PartnerProfileResponse from(ProfileDetails details) {
        return new PartnerProfileResponse(details.id(), details.partnerType(), details.fullName(), details.email(),
                details.phone(), details.countryCode(), details.region(), details.companyName(), details.approvedAt(),
                details.locale(), details.emailNotificationsEnabled(), details.inAppNotificationsEnabled());
    }
}
