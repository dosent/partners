package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.lead.application.LeadService.StaffDetail;
import com.simbirsoft.partners.lead.domain.Lead;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Рабочая карточка лида для сотрудника с внутренними заметками")
public record AdminLeadDetailResponse(UUID id, String number, String companyName, String domainOrTaxId,
        String requestSummary, BigDecimal budgetAmount, String budgetCurrency, LocalDate plannedStartAt,
        String decisionMakerName, String decisionMakerEmail, String description, String status,
        Instant createdAt, Instant updatedAt, List<HistoryItem> history, List<Message> messages) {
    static AdminLeadDetailResponse from(StaffDetail detail) {
        Lead lead = detail.lead();
        return new AdminLeadDetailResponse(lead.id(), lead.number(), lead.companyName(), lead.domainOrTaxId(),
                lead.requestSummary(), lead.budgetAmount(), lead.budgetCurrency(), lead.plannedStartAt(),
                lead.decisionMakerName(), lead.decisionMakerEmail(), lead.description(), lead.status().name(),
                lead.createdAt(), lead.updatedAt(), detail.history().stream()
                        .map(event -> new HistoryItem(event.status().name(), event.changedAt())).toList(),
                detail.messages().stream().map(message -> new Message(message.id(), message.authorType(),
                        message.body(), message.internal(), message.createdAt())).toList());
    }

    public record HistoryItem(String status, Instant changedAt) { }
    @Schema(description = "Сообщение с признаком внутренней видимости")
    public record Message(UUID id, String authorType, String body, boolean internal, Instant createdAt) { }
}
