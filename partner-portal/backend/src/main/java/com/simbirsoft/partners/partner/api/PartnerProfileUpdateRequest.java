package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.partner.application.PartnerProfileService.ProfileUpdate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Разрешённые настройки собственного профиля партнёра")
public record PartnerProfileUpdateRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^[+]?[-0-9(). ]+$") @Size(max = 24) String phone,
        @NotBlank @Pattern(regexp = "^(RU|KZ|EN)$") String locale,
        boolean emailNotificationsEnabled,
        boolean inAppNotificationsEnabled) {
    public ProfileUpdate toCommand() {
        return new ProfileUpdate(fullName, email, phone, locale, emailNotificationsEnabled,
                inAppNotificationsEnabled);
    }
}
