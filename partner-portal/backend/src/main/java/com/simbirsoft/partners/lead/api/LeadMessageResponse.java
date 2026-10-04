package com.simbirsoft.partners.lead.api;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
@Schema(description="Сообщение, доступное партнёру")
public record LeadMessageResponse(UUID id,String authorType,String body,Instant createdAt) {
    static LeadMessageResponse from(LeadMessage message) { return new LeadMessageResponse(message.id(),message.authorType(),message.body(),message.createdAt()); }
}
