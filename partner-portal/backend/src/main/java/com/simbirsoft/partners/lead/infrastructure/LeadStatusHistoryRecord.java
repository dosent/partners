package com.simbirsoft.partners.lead.infrastructure;

import com.simbirsoft.partners.lead.domain.LeadStatus;
import com.simbirsoft.partners.lead.domain.LeadStatusEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="partner_lead_status_history")
class LeadStatusHistoryRecord {
    @Id UUID id;
    @Column(name="lead_id", nullable=false) UUID leadId;
    @Column(nullable=false,length=32) String status;
    @Column(name="changed_at",nullable=false) Instant changedAt;
    @Column(name="changed_by",nullable=false,length=255) String changedBy;
    protected LeadStatusHistoryRecord() { }
    LeadStatusEvent toDomain() { return new LeadStatusEvent(LeadStatus.valueOf(status),changedAt); }
    static LeadStatusHistoryRecord create(UUID leadId,String status,Instant at,String by) {
        var r=new LeadStatusHistoryRecord();r.id=UUID.randomUUID();r.leadId=leadId;r.status=status;r.changedAt=at;r.changedBy=by;return r;
    }
}
