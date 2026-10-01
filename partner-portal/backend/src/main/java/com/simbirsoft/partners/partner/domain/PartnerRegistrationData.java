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
            case SOLE_PROPRIETOR -> requireText(taxIdentifier, "taxIdentifier");
            case LEGAL_ENTITY -> {
                requireText(companyName, "companyName");
                requireText(taxIdentifier, "taxIdentifier");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
