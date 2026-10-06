package com.simbirsoft.partners.staffing.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import jakarta.persistence.EntityManager;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"spring.liquibase.enabled=true"})
class StaffingRequestPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired StaffingRequestJpaRepository repository;
    @Autowired EntityManager entityManager;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void ownerScopedLookupReturnsNoRequestForAnUnmatchedOwner() {
        assertThat(repository.findByIdAndOwnerProfileId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void assigneeSurvivesReloadAndCanBeRemoved() {
        var request = createRequest();
        var assigned = request.withAssignee("resource.manager", Instant.parse("2026-10-06T12:00:00Z"));
        repository.saveAndFlush(StaffingRequestRecord.from(assigned, json));
        entityManager.clear();
        var loaded = repository.findById(request.id()).orElseThrow().toDomain(json);
        assertThat(loaded.assignedResourceManagerUsername()).isEqualTo("resource.manager");
        assertThat(loaded.updatedAt()).isEqualTo(Instant.parse("2026-10-06T12:00:00Z"));
        repository.saveAndFlush(StaffingRequestRecord.from(loaded.withAssignee(null,
                Instant.parse("2026-10-06T13:00:00Z")), json));
        entityManager.clear();
        assertThat(repository.findById(request.id()).orElseThrow().toDomain(json)
                .assignedResourceManagerUsername()).isNull();
    }

    @Test
    void requestWrittenWithoutAssignmentColumnRemainsUnassigned() {
        var request = createRequest();
        var record = StaffingRequestRecord.from(request, json);
        entityManager.createNativeQuery("""
                insert into staffing_request (id, owner_profile_id, lead_id, number, idempotency_key,
                    requirements, status, created_at, updated_at, version)
                values (:id, :owner, :lead, :number, :key, cast(:requirements as jsonb), 'NEW', :now, :now, 0)
                """)
                .setParameter("id", request.id()).setParameter("owner", request.ownerProfileId())
                .setParameter("lead", request.leadId()).setParameter("number", request.number())
                .setParameter("key", request.idempotencyKey()).setParameter("requirements", record.requirements.toString())
                .setParameter("now", request.createdAt()).executeUpdate();
        entityManager.clear();
        assertThat(repository.findById(request.id()).orElseThrow().toDomain(json)
                .assignedResourceManagerUsername()).isNull();
    }

    @Test
    void migrationPreservesRequestsCreatedOnPreviousSchema() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            connection.createStatement().execute("create schema assignment_upgrade");
            connection.setSchema("assignment_upgrade");
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName("assignment_upgrade");
            database.setLiquibaseSchemaName("assignment_upgrade");
            var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database);
            int previousChangeSetCount = liquibase.getDatabaseChangeLog().getChangeSets().size() - 2;
            liquibase.update(previousChangeSetCount, new Contexts(), new LabelExpression());
            connection.createStatement().execute("""
                    insert into partner_application (id, identity_provider, external_subject, status, version)
                    values ('00000000-0000-0000-0000-000000000001', 'YANDEX', 'legacy', 'APPROVED', 0);
                    insert into partner_profile (id, identity_provider, external_subject, application_id,
                        approved_revision_number, approved_at, created_at, updated_at, version)
                    values ('00000000-0000-0000-0000-000000000002', 'YANDEX', 'legacy',
                        '00000000-0000-0000-0000-000000000001', 1, now(), now(), now(), 0);
                    insert into partner_lead (id, owner_profile_id, number, company_name, request_summary,
                        description, status, created_at, updated_at, version)
                    values ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
                        'L-LEGACY', 'Example', 'Java team', 'Development', 'NEW', now(), now(), 0);
                    insert into staffing_request (id, owner_profile_id, lead_id, number, idempotency_key,
                        requirements, status, created_at, updated_at, version)
                    values ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002',
                        '00000000-0000-0000-0000-000000000003', 'SR-LEGACY',
                        '00000000-0000-0000-0000-000000000005', '{}', 'NEW', now(), now(), 7)
                    """);
            liquibase.update(new Contexts(), new LabelExpression());
            try (var rows = connection.createStatement().executeQuery(
                    "select number, version, assigned_resource_manager_username from staffing_request")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("number")).isEqualTo("SR-LEGACY");
                assertThat(rows.getLong("version")).isEqualTo(7);
                assertThat(rows.getString("assigned_resource_manager_username")).isNull();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private StaffingRequest createRequest() {
        UUID applicationId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-06T00:00:00Z");
        entityManager.createNativeQuery("""
                insert into partner_application (id, identity_provider, external_subject, status, version)
                values (:id, 'YANDEX', :subject, 'APPROVED', 0)
                """).setParameter("id", applicationId).setParameter("subject", applicationId.toString()).executeUpdate();
        entityManager.createNativeQuery("""
                insert into partner_profile (id, identity_provider, external_subject, application_id,
                    approved_revision_number, approved_at, created_at, updated_at, version)
                values (:id, 'YANDEX', :subject, :application, 1, :now, :now, :now, 0)
                """).setParameter("id", profileId).setParameter("subject", applicationId.toString())
                .setParameter("application", applicationId).setParameter("now", now).executeUpdate();
        entityManager.createNativeQuery("""
                insert into partner_lead (id, owner_profile_id, number, company_name, request_summary,
                    description, status, created_at, updated_at, version)
                values (:id, :owner, :number, 'Example', 'Java team', 'Development', 'NEW', :now, :now, 0)
                """).setParameter("id", leadId).setParameter("owner", profileId)
                .setParameter("number", "L-" + leadId.toString().substring(0, 8)).setParameter("now", now).executeUpdate();
        return StaffingRequest.create(UUID.randomUUID(), profileId, leadId,
                "SR-" + leadId.toString().substring(0, 8), UUID.randomUUID(),
                new StaffingRequest.RoleRequirements("Java", 1, "Middle", List.of("Java"), List.of(), "Development"),
                new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE, "Russia", false, null, true),
                new StaffingRequest.SelectionRequirements(null, null, true, false, true), now);
    }
}
