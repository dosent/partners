package com.simbirsoft.partners.audit.infrastructure;

import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaStaffingRequestAssignmentAudit implements StaffingRequestAssignmentAudit {
    private final StaffingRequestAssignmentAuditRepository repository;

    public JpaStaffingRequestAssignmentAudit(StaffingRequestAssignmentAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UUID requestId, Action action, String previousUsername, String currentUsername,
            String actor, Instant occurredAt) {
        repository.save(StaffingRequestAssignmentAuditRecord.create(requestId, action, previousUsername,
                currentUsername, actor, occurredAt));
    }
}
