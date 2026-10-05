package com.simbirsoft.partners.access.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PartnerApiKeyJpaRepository extends JpaRepository<PartnerApiKeyRecord, UUID> {
    List<PartnerApiKeyRecord> findByProviderAndSubjectOrderByCreatedAtDesc(String provider, String subject);
}
