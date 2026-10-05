package com.simbirsoft.partners.staffing.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import com.simbirsoft.partners.staffing.domain.StaffingRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "staffing_request")
class StaffingRequestRecord {
    @Id UUID id;
    @Column(name="owner_profile_id", nullable=false, updatable=false) UUID ownerProfileId;
    @Column(name="lead_id", nullable=false, updatable=false) UUID leadId;
    @Column(nullable=false, unique=true, length=32) String number;
    @Column(name="idempotency_key", nullable=false, updatable=false) UUID idempotencyKey;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="requirements", nullable=false, columnDefinition="jsonb") JsonNode requirements;
    @Column(nullable=false, length=32) String status;
    @Column(name="created_at", nullable=false, updatable=false) Instant createdAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
    @Version @Column(nullable=false) long version;
    protected StaffingRequestRecord() { }

    static StaffingRequestRecord from(StaffingRequest value, ObjectMapper json) {
        StaffingRequestRecord r = new StaffingRequestRecord();
        r.id=value.id(); r.ownerProfileId=value.ownerProfileId(); r.leadId=value.leadId(); r.number=value.number();
        r.idempotencyKey=value.idempotencyKey(); r.requirements=json.valueToTree(new Requirements(value.role(), value.work(), value.selection()));
        r.status=value.status().name(); r.createdAt=value.createdAt(); r.updatedAt=value.updatedAt(); r.version=value.version();
        return r;
    }
    StaffingRequest toDomain(ObjectMapper json) {
        try {
            Requirements value=json.treeToValue(requirements, Requirements.class);
            return new StaffingRequest(id, ownerProfileId, leadId, number, idempotencyKey, value.role(), value.work(),
                    value.selection(), StaffingRequestStatus.valueOf(status), createdAt, updatedAt, version);
        } catch (JsonProcessingException e) { throw new IllegalStateException("Stored staffing requirements are invalid", e); }
    }
    record Requirements(StaffingRequest.RoleRequirements role, StaffingRequest.WorkConditions work,
            StaffingRequest.SelectionRequirements selection) { }
}
