package com.simbirsoft.partners.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PartnerProfileAuditRepository extends JpaRepository<PartnerProfileAuditRecord, UUID> { }
