package com.simbirsoft.partners.localization.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegistrationContentDraftJpaRepository extends JpaRepository<RegistrationContentDraftRecord, UUID> {
    Optional<RegistrationContentDraftRecord> findByLocale(String locale);
}
