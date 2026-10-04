package com.simbirsoft.partners.partner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface PartnerProfileJpaRepository extends JpaRepository<PartnerProfileRecord, UUID> {
    Optional<PartnerProfileRecord> findByIdentityProviderAndExternalSubject(String identityProvider, String externalSubject);
}
