package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.lead.application.LeadStore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Страница очереди обработки лидов сотрудниками")
public record AdminLeadPageResponse(List<LeadCardResponse> items, long total, int page, int size) {
    static AdminLeadPageResponse from(LeadStore.StaffPage page) {
        return new AdminLeadPageResponse(page.items().stream().map(LeadCardResponse::from).toList(),
                page.total(), page.page(), page.size());
    }
}
