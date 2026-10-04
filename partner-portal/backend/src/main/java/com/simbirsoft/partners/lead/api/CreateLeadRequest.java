package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.lead.application.LeadService.CreateLead;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description="Данные нового лида партнёра")
public record CreateLeadRequest(
        @NotBlank @Size(max=250) String companyName,
        @Size(max=255) String domainOrTaxId,
        @NotBlank @Size(max=500) String requestSummary,
        @DecimalMin(value="0.0") @Digits(integer=14,fraction=2) BigDecimal budgetAmount,
        @Pattern(regexp="[A-Z]{3}") String budgetCurrency,
        LocalDate plannedStartAt,
        @Size(max=200) String decisionMakerName,
        @Email @Size(max=254) String decisionMakerEmail,
        @NotBlank @Size(max=10000) String description) {
    CreateLead command() { return new CreateLead(companyName,domainOrTaxId,requestSummary,budgetAmount,budgetCurrency,
            plannedStartAt,decisionMakerName,decisionMakerEmail,description); }
}
