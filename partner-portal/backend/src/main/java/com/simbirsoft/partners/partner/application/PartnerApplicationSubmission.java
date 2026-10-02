package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.partner.domain.PartnerType;

/** Application command for creating or resubmitting a partner application. */
public record PartnerApplicationSubmission(
        PartnerType partnerType,
        String fullName,
        String email,
        String phone,
        String countryCode,
        String region,
        String companyName,
        String taxIdentifier,
        String locale,
        int contentVersion,
        String offerVersion,
        String offerSha256,
        boolean offerAccepted,
        String privacyNoticeVersion,
        String privacyNoticeSha256,
        boolean privacyAccepted) { }
