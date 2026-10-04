package com.simbirsoft.partners.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Порт аудита изменений профиля без значений контактных данных. */
public interface PartnerProfileChangeAudit {
    void record(UUID profileId, String provider, String subject, List<String> changedFields, Instant occurredAt);
}
