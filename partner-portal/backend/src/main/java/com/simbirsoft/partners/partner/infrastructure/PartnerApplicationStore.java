package com.simbirsoft.partners.partner.infrastructure;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface PartnerApplicationStore extends Repository<PartnerApplicationRecord, UUID> {
    Optional<PartnerApplicationRecord> findById(UUID id);

    PartnerApplicationRecord saveAndFlush(PartnerApplicationRecord application);
}
