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
import com.simbirsoft.partners.staffing.application.StaffingRequestService;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleLookup;
import com.simbirsoft.partners.audit.infrastructure.JpaStaffingRequestAssignmentAudit;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import com.simbirsoft.partners.lead.application.LeadPartnerContextLookup;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
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
@Import({JpaStaffingRequestStore.class, StaffingRequestService.class, JpaStaffingRequestAssignmentAudit.class,
        StaffingRequestPersistenceTest.JsonConfiguration.class})
class StaffingRequestPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired StaffingRequestJpaRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired StaffingRequestService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffRoleLookup staffRoles;
    @MockitoBean LeadPartnerContextLookup leads;
    @MockitoBean PartnerProfileService profiles;
    @MockitoSpyBean JpaStaffingRequestStore store;
    @MockitoSpyBean JpaStaffingRequestAssignmentAudit audit;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @TestConfiguration static class JsonConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Test void staffPageUsesStableDescendingOrderAcrossOwners() {
        long initialCount = repository.count();
        var first = createRequest();
        var second = createRequest();
        Instant created = Instant.parse("2026-10-07T00:00:00Z");
        var low = withIdentity(first, UUID.fromString("00000000-0000-0000-0000-000000000010"), "SR-LOW", created);
        var high = withIdentity(second, UUID.fromString("00000000-0000-0000-0000-000000000020"), "SR-HIGH", created);
        repository.saveAndFlush(StaffingRequestRecord.from(low, json));
        repository.saveAndFlush(StaffingRequestRecord.from(high, json));
        var page = store.findStaffPage(0, 1);
        assertThat(page.items()).extracting(StaffingRequest::id).containsExactly(high.id());
        assertThat(page.total()).isEqualTo(initialCount + 2);
        assertThat(store.findStaffPage(1, 1).items()).extracting(StaffingRequest::id).containsExactly(low.id());
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void assignmentLifecycleCommitsIncrementedVersionsAndAuditTogether() {
        var request = persistedRequest();
        allowAssignment();
        var actor = new StaffPrincipal("actor");
        var assigned = service.assignResourceManager(actor, request.id(), " RM.ONE ", 0);
        assertThat(assigned.version()).isEqualTo(1);
        assertThat(service.assignResourceManager(actor, request.id(), "rm.one", 1).version()).isEqualTo(1);
        assertThat(auditCount(request.id())).isEqualTo(1);
        var reassigned = service.assignResourceManager(actor, request.id(), "rm.two", 1);
        assertThat(reassigned.version()).isEqualTo(2);
        var unassigned = service.assignResourceManager(actor, request.id(), null, 2);
        assertThat(unassigned.version()).isEqualTo(3);
        assertThat(service.assignResourceManager(actor, request.id(), null, 3).version()).isEqualTo(3);
        assertThat(jdbc.queryForList("select action from staffing_request_assignment_audit where request_id=? order by occurred_at", String.class, request.id()))
                .containsExactly("ASSIGNED", "REASSIGNED", "UNASSIGNED");
        assertThat(jdbc.queryForObject("select assigned_resource_manager_username from staffing_request where id=?", String.class, request.id())).isNull();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackRequestAndInsertedAudit() {
        var request = persistedRequest();
        allowAssignment();
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("audit failure");
        }).when(audit).record(eq(request.id()), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.assignResourceManager(new StaffPrincipal("actor"), request.id(), "rm.one", 0))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select version from staffing_request where id=?", Long.class, request.id())).isZero();
        assertThat(jdbc.queryForObject("select assigned_resource_manager_username from staffing_request where id=?", String.class, request.id())).isNull();
        assertThat(auditCount(request.id())).isZero();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void optimisticLockRaceKeepsWinnerAndRollsBackLoserWithoutAudit() {
        var request = persistedRequest();
        allowAssignment();
        doAnswer(call -> {
            var loaded = call.callRealMethod();
            var concurrent = new TransactionTemplate(transactions);
            concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            concurrent.executeWithoutResult(status -> jdbc.update("update staffing_request set assigned_resource_manager_username='rm.two', version=version+1 where id=?", request.id()));
            return loaded;
        }).when(store).findAny(request.id());
        assertThatThrownBy(() -> service.assignResourceManager(new StaffPrincipal("actor"), request.id(), "rm.one", 0))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(jdbc.queryForObject("select version from staffing_request where id=?", Long.class, request.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select assigned_resource_manager_username from staffing_request where id=?", String.class, request.id())).isEqualTo("rm.two");
        assertThat(auditCount(request.id())).isZero();
    }

    private StaffingRequest persistedRequest() {
        return new TransactionTemplate(transactions).execute(status ->
                repository.saveAndFlush(StaffingRequestRecord.from(createRequest(), json)).toDomain(json));
    }
    private void allowAssignment() {
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(staffRoles.hasRole("rm.one", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
        when(staffRoles.hasRole("rm.two", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
    }
    private long auditCount(UUID id) {
        return jdbc.queryForObject("select count(*) from staffing_request_assignment_audit where request_id=?", Long.class, id);
    }
    private static StaffingRequest withIdentity(StaffingRequest r, UUID id, String number, Instant created) {
        return new StaffingRequest(id, r.ownerProfileId(), r.leadId(), number, r.idempotencyKey(), r.role(), r.work(), r.selection(),
                r.status(), created, created, 0, null);
    }

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
