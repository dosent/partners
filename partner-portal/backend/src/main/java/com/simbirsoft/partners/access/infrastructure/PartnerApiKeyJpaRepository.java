package com.simbirsoft.partners.access.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PartnerApiKeyJpaRepository extends JpaRepository<PartnerApiKeyRecord, UUID> {
    List<PartnerApiKeyRecord> findByProviderAndSubjectOrderByCreatedAtDesc(String provider, String subject);
    @Modifying
    @Query("update PartnerApiKeyRecord k set k.lastUsedAt = :usedAt where k.id = :id and k.revokedAt is null")
    int markUsedIfActive(@Param("id") UUID id, @Param("usedAt") java.time.Instant usedAt);
}
