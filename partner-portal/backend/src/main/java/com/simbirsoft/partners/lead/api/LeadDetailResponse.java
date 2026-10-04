package com.simbirsoft.partners.lead.api;
import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import com.simbirsoft.partners.lead.domain.LeadStatusEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
@Schema(description="Детали собственного лида без внутренних заметок и совпадений")
public record LeadDetailResponse(UUID id,String number,String companyName,String domainOrTaxId,String requestSummary,
        BigDecimal budgetAmount,String budgetCurrency,LocalDate plannedStartAt,String decisionMakerName,
        String decisionMakerEmail,String description,String status,Instant createdAt,Instant updatedAt,
        List<HistoryItem> history,List<LeadMessageResponse> messages,List<Attachment> attachments) {
    static LeadDetailResponse from(Lead lead,List<LeadStatusEvent> history,List<LeadMessage> messages) {
        return from(lead, history, messages, List.of());
    }
    static LeadDetailResponse from(Lead lead,List<LeadStatusEvent> history,List<LeadMessage> messages,List<Attachment> attachments) {
        return new LeadDetailResponse(lead.id(),lead.number(),lead.companyName(),lead.domainOrTaxId(),lead.requestSummary(),
                lead.budgetAmount(),lead.budgetCurrency(),lead.plannedStartAt(),lead.decisionMakerName(),lead.decisionMakerEmail(),
                lead.description(),lead.status().name(),lead.createdAt(),lead.updatedAt(),
                history.stream().map(h -> new HistoryItem(h.status().name(),h.changedAt())).toList(),messages.stream().map(LeadMessageResponse::from).toList(),attachments);
    }
    public record HistoryItem(String status,Instant changedAt) { }
    @Schema(description="Разрешённое партнёру вложение")
    public record Attachment(UUID id,String fileName,String scanStatus,String downloadUrl) { }
}
