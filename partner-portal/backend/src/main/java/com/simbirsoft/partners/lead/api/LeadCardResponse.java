package com.simbirsoft.partners.lead.api;
import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Schema(description="Партнёрская карточка лида")
public record LeadCardResponse(UUID id,String number,String companyName,String requestSummary,BigDecimal budgetAmount,
        String budgetCurrency,LeadStatus status,Instant updatedAt,String assignedManagerName) {
    static LeadCardResponse from(Lead lead) { return new LeadCardResponse(lead.id(),lead.number(),lead.companyName(),
            lead.requestSummary(),lead.budgetAmount(),lead.budgetCurrency(),lead.status(),lead.updatedAt(),null); }
}
