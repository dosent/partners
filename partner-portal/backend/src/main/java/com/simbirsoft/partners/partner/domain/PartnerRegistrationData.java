package com.simbirsoft.partners.partner.domain;

import java.util.Objects;

public record PartnerRegistrationData(
        PartnerType partnerType,
        String fullName,
        String email,
        String phone,
        String countryCode,
        String region,
        String companyName,
        String taxIdentifier) {

    public PartnerRegistrationData {
        Objects.requireNonNull(partnerType, "partnerType is required");
        requireText(fullName, "fullName");
        requireText(email, "email");
        requireText(phone, "phone");
        requireText(countryCode, "countryCode");
        requireText(region, "region");

        switch (partnerType) {
            case INDIVIDUAL -> {
                if (companyName != null || taxIdentifier != null) {
                    throw new IllegalArgumentException("Individuals must not provide business requisites");
                }
            }
            case SOLE_PROPRIETOR -> {
                requireText(taxIdentifier, "taxIdentifier");
                validateTaxIdentifier(countryCode, partnerType, taxIdentifier);
            }
            case LEGAL_ENTITY -> {
                requireText(companyName, "companyName");
                requireText(taxIdentifier, "taxIdentifier");
                validateTaxIdentifier(countryCode, partnerType, taxIdentifier);
            }
        }
    }

    private static void validateTaxIdentifier(String countryCode, PartnerType partnerType, String value) {
        if (value == null || !value.matches("[0-9]+")) {
            throw new InvalidTaxIdentifierException("taxIdentifier must contain digits only");
        }
        String country = countryCode.trim().toUpperCase(java.util.Locale.ROOT);
        boolean valid = switch (country) {
            case "RU" -> partnerType == PartnerType.LEGAL_ENTITY
                    ? validRussianInn(value, 10) : validRussianInn(value, 12);
            case "KZ" -> value.length() == 12 && validKazakhIinBin(value);
            default -> throw new InvalidTaxIdentifierException("Tax identifier jurisdiction is unsupported");
        };
        if (!valid) {
            throw new InvalidTaxIdentifierException("taxIdentifier has an invalid format or checksum");
        }
    }

    private static boolean validRussianInn(String value, int expectedLength) {
        if (value.length() != expectedLength) return false;
        if (expectedLength == 10) {
            return checkDigit(value, 9, new int[]{2, 4, 10, 3, 5, 9, 4, 6, 8}) == digit(value, 9);
        }
        int check11 = checkDigit(value, 10, new int[]{7, 2, 4, 10, 3, 5, 9, 4, 6, 8});
        int check12 = checkDigit(value, 11, new int[]{3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8});
        return check11 == digit(value, 10) && check12 == digit(value, 11);
    }

    private static boolean validKazakhIinBin(String value) {
        int first = weightedModulo11(value, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
        if (first == digit(value, 11)) return true;
        if (first != 10) return false;
        int second = weightedModulo11(value, new int[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 1, 2});
        return second != 10 && second == digit(value, 11);
    }

    private static int checkDigit(String value, int count, int[] weights) {
        int sum = 0;
        for (int index = 0; index < count; index++) sum += digit(value, index) * weights[index];
        return (sum % 11) % 10;
    }

    private static int weightedModulo11(String value, int[] weights) {
        int sum = 0;
        for (int index = 0; index < weights.length; index++) sum += digit(value, index) * weights[index];
        return sum % 11;
    }

    private static int digit(String value, int index) {
        return value.charAt(index) - '0';
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
