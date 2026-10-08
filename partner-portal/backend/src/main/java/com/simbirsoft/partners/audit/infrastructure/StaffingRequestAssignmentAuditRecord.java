package com.simbirsoft.partners.audit.infrastructure;

import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit.Action;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "staffing_request_assignment_audit")
public class StaffingRequestAssignmentAuditRecord {
    @Id
    private UUID id;
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private Action action;
    @Column(name = "previous_username", updatable = false, length = 255)
    private String previousUsername;
    @Column(name = "current_username", updatable = false, length = 255)
    private String currentUsername;
    @Column(nullable = false, updatable = false, length = 255)
    private String actor;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StaffingRequestAssignmentAuditRecord() { }

    static StaffingRequestAssignmentAuditRecord create(UUID requestId, Action action, String previousUsername,
            String currentUsername, String actor, Instant occurredAt) {
        var record = new StaffingRequestAssignmentAuditRecord();
        record.id = UUID.randomUUID();
        record.requestId = requestId;
        record.action = action;
        record.previousUsername = previousUsername;
        record.currentUsername = currentUsername;
        record.actor = actor;
        record.occurredAt = occurredAt;
        return record;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public Action getAction() { return action; }
    public String getPreviousUsername() { return previousUsername; }
    public String getCurrentUsername() { return currentUsername; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
}
