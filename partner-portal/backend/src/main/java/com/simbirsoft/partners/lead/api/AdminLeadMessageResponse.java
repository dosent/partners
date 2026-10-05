package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.lead.domain.LeadMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Созданный комментарий лида с признаком внутренней видимости")
public record AdminLeadMessageResponse(UUID id, String authorType, String body, boolean internal, Instant createdAt) {
    static AdminLeadMessageResponse from(LeadMessage message) {
        return new AdminLeadMessageResponse(message.id(), message.authorType(), message.body(),
                message.internal(), message.createdAt());
    }
}
