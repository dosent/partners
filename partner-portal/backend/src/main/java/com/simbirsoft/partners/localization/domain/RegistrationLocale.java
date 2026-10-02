package com.simbirsoft.partners.localization.domain;

import java.util.Locale;
import java.util.Optional;

/** Локали публичной регистрации. */
public enum RegistrationLocale {
    RU("RU", "simbirsoft"),
    KZ("KZ", "17soft"),
    EN("EN", "simbirsoft");

    private final String value;
    private final String requiredLogoAssetKey;

    RegistrationLocale(String value, String requiredLogoAssetKey) {
        this.value = value;
        this.requiredLogoAssetKey = requiredLogoAssetKey;
    }

    public String value() { return value; }
    public String requiredLogoAssetKey() { return requiredLogoAssetKey; }

    public static Optional<RegistrationLocale> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(value.trim().toUpperCase(Locale.ROOT)).map(RegistrationLocale::valueOf);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** В публичном потоке неподдерживаемые значения используют русскую локаль по умолчанию. */
    public static RegistrationLocale publicOrDefault(String value) {
        return parse(value).orElse(RU);
    }
}
