package com.simbirsoft.partners.lead.infrastructure;

import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "partner_lead")
class LeadRecord {
    @Id UUID id;
    @Column(name="owner_profile_id", nullable=false, updatable=false) UUID ownerProfileId;
    @Column(nullable=false, unique=true, length=32) String number;
    @Column(name="company_name", nullable=false, length=250) String companyName;
    @Column(name="domain_or_tax_id", length=255) String domainOrTaxId;
    @Column(name="request_summary", nullable=false, length=500) String requestSummary;
    @Column(name="budget_amount", precision=16, scale=2) BigDecimal budgetAmount;
    @Column(name="budget_currency", length=3) String budgetCurrency;
    @Column(name="planned_start_at") LocalDate plannedStartAt;
    @Column(name="decision_maker_name", length=200) String decisionMakerName;
    @Column(name="decision_maker_email", length=254) String decisionMakerEmail;
    @Column(nullable=false, length=10000) String description;
    @Column(nullable=false, length=32) String status;
    @Column(name="created_at", nullable=false, updatable=false) Instant createdAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
    @Version @Column(nullable=false) long version;
    protected LeadRecord() { }
    static LeadRecord from(Lead lead) {
        LeadRecord r = new LeadRecord(); r.id=lead.id(); r.ownerProfileId=lead.ownerProfileId(); r.number=lead.number();
        r.companyName=lead.companyName(); r.domainOrTaxId=lead.domainOrTaxId(); r.requestSummary=lead.requestSummary();
        r.budgetAmount=lead.budgetAmount(); r.budgetCurrency=lead.budgetCurrency(); r.plannedStartAt=lead.plannedStartAt();
        r.decisionMakerName=lead.decisionMakerName(); r.decisionMakerEmail=lead.decisionMakerEmail();
        r.description=lead.description(); r.status=lead.status().name(); r.createdAt=lead.createdAt(); r.updatedAt=lead.updatedAt(); return r;
    }
    void changeStatus(LeadStatus nextStatus, Instant changedAt) { status=nextStatus.name(); updatedAt=changedAt; }
    Lead toDomain() { return new Lead(id,ownerProfileId,number,companyName,domainOrTaxId,requestSummary,budgetAmount,budgetCurrency,
            plannedStartAt,decisionMakerName,decisionMakerEmail,description,LeadStatus.valueOf(status),createdAt,updatedAt); }
}
