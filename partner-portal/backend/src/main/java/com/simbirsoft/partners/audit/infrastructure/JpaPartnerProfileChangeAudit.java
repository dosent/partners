package com.simbirsoft.partners.audit.infrastructure;

import com.simbirsoft.partners.audit.application.PartnerProfileChangeAudit;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JpaPartnerProfileChangeAudit implements PartnerProfileChangeAudit {
    private final PartnerProfileAuditRepository repository;

    public JpaPartnerProfileChangeAudit(PartnerProfileAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UUID profileId, String provider, String subject, List<String> changedFields,
            Instant occurredAt) {
        String fieldList = changedFields.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
        repository.save(PartnerProfileAuditRecord.create(profileId, provider, subject, fieldList, occurredAt));
    }
}
