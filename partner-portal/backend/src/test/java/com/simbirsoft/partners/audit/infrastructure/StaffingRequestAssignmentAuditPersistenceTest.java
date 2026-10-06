package com.simbirsoft.partners.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit.Action;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@Import(JpaStaffingRequestAssignmentAudit.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.liquibase.enabled=true")
class StaffingRequestAssignmentAuditPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired StaffingRequestAssignmentAudit audit;
    @Autowired EntityManager entityManager;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void recordsAssignmentReassignmentAndRemovalWithoutOverwritingHistory() {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-06T12:00:00Z");
        audit.record(requestId, Action.ASSIGNED, null, "manager.one", "admin", now);
        audit.record(requestId, Action.REASSIGNED, "manager.one", "manager.two", "admin", now.plusSeconds(1));
        audit.record(requestId, Action.UNASSIGNED, "manager.two", null, "admin", now.plusSeconds(2));
        entityManager.flush();
        entityManager.clear();
        var rows = entityManager.createQuery("select a from StaffingRequestAssignmentAuditRecord a "
                + "where a.requestId = :requestId order by a.occurredAt", StaffingRequestAssignmentAuditRecord.class)
                .setParameter("requestId", requestId).getResultList();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getId).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getRequestId).containsOnly(requestId);
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getAction)
                .containsExactly(Action.ASSIGNED, Action.REASSIGNED, Action.UNASSIGNED);
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getPreviousUsername)
                .containsExactly(null, "manager.one", "manager.two");
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getCurrentUsername)
                .containsExactly("manager.one", "manager.two", null);
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getActor).containsOnly("admin");
        assertThat(rows).extracting(StaffingRequestAssignmentAuditRecord::getOccurredAt)
                .containsExactly(now, now.plusSeconds(1), now.plusSeconds(2));
    }
}
