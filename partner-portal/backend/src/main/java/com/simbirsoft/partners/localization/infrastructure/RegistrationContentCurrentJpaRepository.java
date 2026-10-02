package com.simbirsoft.partners.localization.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegistrationContentCurrentJpaRepository extends JpaRepository<RegistrationContentCurrentRecord, String> {
    @Override
    @EntityGraph(attributePaths = "publication")
    Optional<RegistrationContentCurrentRecord> findById(String locale);
}
