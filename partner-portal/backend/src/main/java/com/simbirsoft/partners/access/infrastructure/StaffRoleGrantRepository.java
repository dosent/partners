package com.simbirsoft.partners.access.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRoleGrantRepository extends JpaRepository<StaffRoleGrantRecord, UUID> {
    List<StaffRoleGrantRecord> findAllByUsername(String username);
    void deleteAllByUsername(String username);
}
