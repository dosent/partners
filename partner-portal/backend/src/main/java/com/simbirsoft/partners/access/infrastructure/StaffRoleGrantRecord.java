package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "staff_role_grant")
public class StaffRoleGrantRecord {
    @Id private UUID id;
    @Column(nullable = false, length = 255) private String username;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private StaffRole role;
    protected StaffRoleGrantRecord() {}
    public StaffRoleGrantRecord(String username, StaffRole role) {
        this.id = UUID.randomUUID(); this.username = username; this.role = role;
    }
    public String username() { return username; }
    public StaffRole role() { return role; }
}
