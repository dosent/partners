package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatusEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_application_status_history", uniqueConstraints = @UniqueConstraint(
        name = "uq_partner_application_status_event", columnNames = {"application_id", "event_number"}))
public class PartnerApplicationStatusRecord {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PartnerApplicationRecord application;

    @Column(name = "event_number", nullable = false)
    private int eventNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerApplicationStatus status;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected PartnerApplicationStatusRecord() {
    }

    PartnerApplicationStatusRecord(PartnerApplicationRecord application, int eventNumber,
                                   PartnerApplicationStatus status, String changedBy, String reason,
                                   Instant changedAt) {
        this.id = UUID.randomUUID();
        this.application = application;
        this.eventNumber = eventNumber;
        this.status = status;
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    PartnerApplicationStatusEvent toDomain() {
        return new PartnerApplicationStatusEvent(status, changedBy, reason, changedAt);
    }
}
