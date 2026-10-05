package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.PartnerApiKeyStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaPartnerApiKeyStore implements PartnerApiKeyStore {
    private final PartnerApiKeyJpaRepository repository;
    JpaPartnerApiKeyStore(PartnerApiKeyJpaRepository repository) { this.repository=repository; }
    @Override public void save(Credential credential) { repository.saveAndFlush(PartnerApiKeyRecord.from(credential)); }
    @Override public Optional<Credential> findById(UUID id) { return repository.findById(id).map(PartnerApiKeyRecord::toCredential); }
    @Override public List<Credential> findByOwner(String provider, String subject) {
        return repository.findByProviderAndSubjectOrderByCreatedAtDesc(provider, subject).stream().map(PartnerApiKeyRecord::toCredential).toList();
    }
    @Override public void update(Credential credential) {
        var row=repository.findById(credential.id()).orElseThrow(); row.update(credential); repository.saveAndFlush(row);
    }
}
