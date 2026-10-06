package com.simbirsoft.partners.audit.application;

import java.time.Instant;
import java.util.UUID;

public interface StaffingRequestAssignmentAudit {
    enum Action { ASSIGNED, REASSIGNED, UNASSIGNED }

    void record(UUID requestId, Action action, String previousUsername, String currentUsername,
            String actor, Instant occurredAt);
}
