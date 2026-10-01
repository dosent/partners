package com.simbirsoft.partners.access.api;

import jakarta.validation.constraints.NotBlank;

public record StaffLoginRequest(@NotBlank String username, @NotBlank String password) {
}
