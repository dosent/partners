package com.simbirsoft.partners.partner.infrastructure;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface PartnerApplicationStore extends Repository<PartnerApplicationRecord, UUID> {
    Optional<PartnerApplicationRecord> findById(UUID id);

    Optional<PartnerApplicationRecord> findByIdentityProviderAndExternalSubject(String identityProvider,
                                                                                  String externalSubject);

    PartnerApplicationRecord saveAndFlush(PartnerApplicationRecord application);
}
