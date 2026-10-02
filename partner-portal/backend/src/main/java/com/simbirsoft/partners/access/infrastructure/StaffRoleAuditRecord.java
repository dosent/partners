package com.simbirsoft.partners.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_role_audit")
public class StaffRoleAuditRecord {
    public enum Action { GRANTED, REVOKED }
    @Id private UUID id;
    @Column(nullable = false, length = 255) private String username;
    @Column(nullable = false, length = 40) private String role;
    @Column(nullable = false, length = 255) private String actor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Action action;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;
    protected StaffRoleAuditRecord() {}
    public StaffRoleAuditRecord(String username, String role, String actor, Action action) {
        this.id = UUID.randomUUID(); this.username = username; this.role = role; this.actor = actor;
        this.action = action; this.changedAt = Instant.now();
    }
}
