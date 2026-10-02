package com.simbirsoft.partners.localization.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RegistrationContentPublicationJpaRepository extends JpaRepository<RegistrationContentPublicationRecord, UUID> {
    @Query("select coalesce(max(p.contentVersion), 0) from RegistrationContentPublicationRecord p where p.locale = :locale")
    int maxVersion(@Param("locale") String locale);
}
