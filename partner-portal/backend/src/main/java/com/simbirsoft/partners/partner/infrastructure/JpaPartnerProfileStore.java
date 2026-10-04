package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.partner.application.PartnerProfileStore;
import com.simbirsoft.partners.partner.domain.PartnerProfile;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
class JpaPartnerProfileStore implements PartnerProfileStore {
    private final PartnerProfileJpaRepository repository;

    JpaPartnerProfileStore(PartnerProfileJpaRepository repository) { this.repository = repository; }

    @Override
    public Optional<PartnerProfile> findByIdentity(String identityProvider, String externalSubject) {
        return repository.findByIdentityProviderAndExternalSubject(identityProvider, externalSubject)
                .map(PartnerProfileRecord::toDomain);
    }

    @Override
    public PartnerProfile save(PartnerProfile profile) {
        var existing = repository.findByIdentityProviderAndExternalSubject(
                profile.identityProvider(), profile.externalSubject());
        var saved = existing.map(record -> {
            record.updateFrom(profile);
            return repository.saveAndFlush(record);
        }).orElseGet(() -> repository.saveAndFlush(PartnerProfileRecord.from(profile)));
        return saved.toDomain();
    }
}
