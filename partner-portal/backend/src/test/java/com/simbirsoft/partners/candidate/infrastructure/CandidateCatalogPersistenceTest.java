package com.simbirsoft.partners.candidate.infrastructure;
import com.simbirsoft.partners.candidate.domain.*;
import com.simbirsoft.partners.candidate.application.*;
import com.simbirsoft.partners.shared.persistence.QueryHelper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest @Testcontainers
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaCandidateProfileStore.class,JpaCandidateCreationStore.class,QueryHelper.class})
class CandidateCatalogPersistenceTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired CandidateProfileStore profiles;
    @Autowired CandidateCreationStore creations;
    private CandidateProfile create(String key,String role,CandidateAvailability availability) {
        var now=Instant.parse("2026-10-30T10:00:00Z");var id=UUID.randomUUID();
        return profiles.saveAndFlush(new CandidateProfile(id,"C-"+id.toString().replace("-",""),role,null,List.of("Java"),0,"Test",availability,key,now,now,"actor","actor",0));
    }
    @Test void permitsMultipleNullKeysAndCaseSensitiveValues() {
        create(null,"Java",CandidateAvailability.AVAILABLE);create(null,"Java",CandidateAvailability.AVAILABLE);
        create("abc","Java",CandidateAvailability.AVAILABLE);create("ABC","Java",CandidateAvailability.AVAILABLE);
        assertThat(profiles.page("",null,0,20).total()).isEqualTo(4);
    }
    @Test void enforcesUniqueNonEmptyKey() {
        create("one","Java",CandidateAvailability.AVAILABLE);
        assertThatThrownBy(()->create("one","QA",CandidateAvailability.AVAILABLE)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void searchesLiterallyAndFiltersAvailability() {
        var first=create(null,"Java 100%",CandidateAvailability.AVAILABLE);
        create(null,"Java",CandidateAvailability.UNAVAILABLE);
        assertThat(profiles.page("%",null,0,20).items()).extracting(CandidateProfile::id).containsExactly(first.id());
        assertThat(profiles.page("_",null,0,20).total()).isZero();
        assertThat(profiles.page("java",CandidateAvailability.AVAILABLE,0,1).total()).isEqualTo(1);
    }
    @Test void reservesCreationExactlyOnce() {
        var key=UUID.randomUUID();var id=UUID.randomUUID();
        assertThat(creations.reserve("actor",key,"hash",id).created()).isTrue();
        var replay=creations.reserve("actor",key,"hash",UUID.randomUUID());
        assertThat(replay.created()).isFalse();assertThat(replay.candidateId()).isEqualTo(id);
    }

    @Test void migratesPreviousSchemaWithoutChangingExistingRecords() throws Exception {
        try(var connection=java.sql.DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword())) {
            connection.createStatement().execute("create schema candidate_upgrade");connection.setSchema("candidate_upgrade");
            var database=liquibase.database.DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));
            database.setDefaultSchemaName("candidate_upgrade");database.setLiquibaseSchemaName("candidate_upgrade");
            var migration=new liquibase.Liquibase("db/changelog/db.changelog-master.yaml",new liquibase.resource.ClassLoaderResourceAccessor(),database);
            migration.update(migration.getDatabaseChangeLog().getChangeSets().size()-1,new liquibase.Contexts(),new liquibase.LabelExpression());
            connection.createStatement().execute("insert into partner_application (id,identity_provider,external_subject,status,version) values ('00000000-0000-0000-0000-000000000099','YANDEX','candidate-legacy','APPROVED',7)");
            migration.update(new liquibase.Contexts(),new liquibase.LabelExpression());
            try(var result=connection.createStatement().executeQuery("select version from partner_application where external_subject='candidate-legacy'")) {
                assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isEqualTo(7);
            }
            try(var result=connection.createStatement().executeQuery("select count(*) from candidate_profile")) {
                assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isZero();
            }
        }
    }
}
