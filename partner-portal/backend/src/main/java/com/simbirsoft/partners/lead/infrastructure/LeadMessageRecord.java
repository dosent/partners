package com.simbirsoft.partners.lead.infrastructure;

import com.simbirsoft.partners.lead.domain.LeadMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="partner_lead_message")
class LeadMessageRecord {
    @Id UUID id;
    @Column(name="lead_id",nullable=false) UUID leadId;
    @Column(name="author_type",nullable=false,length=16) String authorType;
    @Column(nullable=false,length=5000) String body;
    @Column(nullable=false) boolean internal;
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected LeadMessageRecord() { }
    LeadMessage toDomain() { return new LeadMessage(id,leadId,authorType,body,internal,createdAt); }
}
