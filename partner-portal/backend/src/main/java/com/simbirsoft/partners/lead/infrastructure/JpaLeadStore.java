package com.simbirsoft.partners.lead.infrastructure;

import com.simbirsoft.partners.lead.application.LeadStore;
import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import com.simbirsoft.partners.lead.domain.LeadStatusEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!auth-test")
class JpaLeadStore implements LeadStore {
    private static final Instant MIN_INSTANT = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T23:59:59.999999Z");
    private final LeadJpaRepository leads; private final LeadStatusHistoryJpaRepository history; private final LeadMessageJpaRepository messages;
    JpaLeadStore(LeadJpaRepository leads,LeadStatusHistoryJpaRepository history,LeadMessageJpaRepository messages) {
        this.leads=leads;this.history=history;this.messages=messages;
    }
    @Override @Transactional public Lead save(Lead lead) {
        var record=leads.save(LeadRecord.from(lead));
        history.save(LeadStatusHistoryRecord.create(lead.id(),lead.status().name(),lead.createdAt(),"PARTNER"));
        return record.toDomain();
    }
    @Override @Transactional(readOnly=true) public Optional<Lead> findAny(UUID id) { return leads.findById(id).map(LeadRecord::toDomain); }
    @Override @Transactional public Lead changeStatus(UUID id,LeadStatus expected,LeadStatus next,String changedBy,Instant changedAt) {
        LeadRecord record=leads.findForUpdateById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (!LeadStatus.valueOf(record.status).equals(expected))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Lead status changed concurrently");
        record.changeStatus(next,changedAt);
        LeadRecord saved=leads.saveAndFlush(record);
        history.save(LeadStatusHistoryRecord.create(id,next.name(),changedAt,changedBy));
        return saved.toDomain();
    }
    @Override @Transactional(readOnly=true) public Optional<Lead> findOwned(UUID id,UUID owner) {
        return leads.findByIdAndOwnerProfileId(id,owner).map(LeadRecord::toDomain);
    }
    @Override @Transactional(readOnly=true) public Page findPage(UUID owner,List<LeadStatus> statuses,String query,Instant from,Instant to,
            Instant before,UUID beforeId,int limit) {
        String normalized=query==null||query.isBlank()?"":query.trim();
        var records=leads.findPage(owner,statuses.stream().map(Enum::name).toList(),normalized,
                from == null ? MIN_INSTANT : from, to == null ? MAX_INSTANT : to,
                before == null ? MAX_INSTANT : before, beforeId == null ? new UUID(0,0) : beforeId,
                before != null, PageRequest.of(0,limit+1));
        boolean more=records.size()>limit;
        return new Page(records.stream().limit(limit).map(LeadRecord::toDomain).toList(),more);
    }
    @Override @Transactional(readOnly=true) public long count(UUID owner,List<LeadStatus> statuses,String query,Instant from,Instant to) {
        return leads.countMatching(owner,statuses.stream().map(Enum::name).toList(),query==null||query.isBlank()?"":query.trim(),
                from == null ? MIN_INSTANT : from, to == null ? MAX_INSTANT : to);
    }
    @Override @Transactional(readOnly=true) public List<Lead> findAllOwned(UUID owner) { return List.of(); }
    @Override @Transactional(readOnly=true) public List<LeadStatusEvent> statusHistory(UUID leadId) {
        return history.findByLeadIdOrderByChangedAtAsc(leadId).stream().map(LeadStatusHistoryRecord::toDomain).toList();
    }
    @Override @Transactional(readOnly=true) public List<LeadMessage> publicMessages(UUID leadId) {
        return messages.findByLeadIdOrderByCreatedAtAsc(leadId).stream().map(LeadMessageRecord::toDomain)
                .filter(message -> !message.internal()).toList();
    }
    @Override @Transactional public LeadMessage addMessage(UUID leadId,UUID owner,String body,Instant createdAt) {
        if (leads.findByIdAndOwnerProfileId(leadId,owner).isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        var lead=leads.findByIdAndOwnerProfileId(leadId,owner).orElseThrow();
        lead.updatedAt=createdAt; leads.save(lead);
        var r=new LeadMessageRecord();r.id=UUID.randomUUID();r.leadId=leadId;r.authorType="PARTNER";r.body=body;r.createdAt=createdAt;r.internal=false;
        return messages.saveAndFlush(r).toDomain();
    }
}
