package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.staffing.application.StaffingRequestStore;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import com.simbirsoft.partners.staffing.domain.StaffingRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description="Страница очереди заявок для сотрудников")
public record StaffingRequestQueuePageResponse(List<Item> items, int page, int size, long total) {
    static StaffingRequestQueuePageResponse from(StaffingRequestStore.StaffPage page) {
        return new StaffingRequestQueuePageResponse(page.items().stream().map(Item::from).toList(),
                page.page(), page.size(), page.total());
    }

    @Schema(description="Краткий контекст заявки и текущее назначение")
    public record Item(UUID id, String number, String title, int headcount, StaffingRequestStatus status,
            Instant createdAt, @Schema(nullable=true) String assignedResourceManagerUsername, long version) {
        static Item from(StaffingRequest request) {
            return new Item(request.id(), request.number(), request.role().title(), request.role().headcount(),
                    request.status(), request.createdAt(), request.assignedResourceManagerUsername(), request.version());
        }
    }
}
