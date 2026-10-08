package com.simbirsoft.partners.audit.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;

interface StaffingRequestAssignmentAuditRepository extends Repository<StaffingRequestAssignmentAuditRecord, UUID> {
    StaffingRequestAssignmentAuditRecord save(StaffingRequestAssignmentAuditRecord record);
}
