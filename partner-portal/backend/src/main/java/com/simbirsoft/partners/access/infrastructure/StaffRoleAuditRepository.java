package com.simbirsoft.partners.access.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRoleAuditRepository extends JpaRepository<StaffRoleAuditRecord, UUID> {}
