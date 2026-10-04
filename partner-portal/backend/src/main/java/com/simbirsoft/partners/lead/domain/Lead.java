package com.simbirsoft.partners.lead.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Lead(UUID id, UUID ownerProfileId, String number, String companyName, String domainOrTaxId,
        String requestSummary, BigDecimal budgetAmount, String budgetCurrency, LocalDate plannedStartAt,
        String decisionMakerName, String decisionMakerEmail, String description, LeadStatus status,
        Instant createdAt, Instant updatedAt) { }
