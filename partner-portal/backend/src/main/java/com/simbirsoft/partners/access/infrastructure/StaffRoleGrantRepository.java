package com.simbirsoft.partners.access.infrastructure;

import java.util.List;
import java.util.UUID;
import com.simbirsoft.partners.access.application.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRoleGrantRepository extends JpaRepository<StaffRoleGrantRecord, UUID> {
    List<StaffRoleGrantRecord> findAllByUsername(String username);
    List<StaffRoleGrantRecord> findAllByRoleOrderByUsernameAsc(StaffRole role);
    List<StaffRoleGrantRecord> findAllByOrderByUsernameAscRoleAsc();
    void deleteAllByUsername(String username);
}
