package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.CandidateCv;
import com.simbirsoft.partners.document.application.DocumentValidationService;
import com.simbirsoft.partners.document.application.MalwareScanner;
import com.simbirsoft.partners.document.application.ObjectStorage;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CandidateCvAdapter.class, CandidateCvPersistenceTest.StorageConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CandidateCvPersistenceTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired CandidateCv cvs;
    @Autowired CandidateCvJpaRepository records;
    @Autowired MemoryStorage storage;
    @Autowired PlatformTransactionManager transactions;
    private final UUID candidateId = UUID.randomUUID();
    private final Instant checkedAt = Instant.parse("2026-10-30T08:00:00Z");
    private final DocumentValidationService validation = new DocumentValidationService(bytes -> MalwareScanner.Result.CLEAN);

    @BeforeEach void clear() { records.deleteAll(); storage.objects.clear(); }

    @Test void committedReplacementKeepsPreviousFileAndItsMetadata() {
        var old = cvs.store(candidateId, validation.prepare("old.pdf", "application/pdf", "%PDF-1.4 old".getBytes()), "staff", checkedAt);
        var next = cvs.store(candidateId, validation.prepare("new.pdf", "application/pdf", "%PDF-1.4 new".getBytes()), "staff", checkedAt.plusSeconds(1));
        assertThat(next.version()).isEqualTo(2);
        assertThat(cvs.current(candidateId)).contains(next);
        assertThat(cvs.download(candidateId).bytes()).isEqualTo("%PDF-1.4 new".getBytes());
        assertThat(records.findById(old.id())).get().satisfies(record -> {
            assertThat(record.fileName).isEqualTo("old.pdf");
            assertThat(storage.objects.get(record.objectKey)).isEqualTo("%PDF-1.4 old".getBytes());
        });
        assertThat(records.count()).isEqualTo(2);
        assertThat(storage.objects).hasSize(2);
    }

    @Test void realRollbackRestoresPreviousMetadataAndRemovesOnlyNewObject() {
        var old = cvs.store(candidateId, validation.prepare("old.pdf", "application/pdf", "%PDF-1.4 old".getBytes()), "staff", checkedAt);
        String oldObjectKey = records.findById(old.id()).orElseThrow().objectKey;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            cvs.store(candidateId, validation.prepare("new.pdf", "application/pdf", "%PDF-1.4 new".getBytes()), "staff", checkedAt);
            assertThat(storage.objects).hasSize(2);
            status.setRollbackOnly();
        });
        assertThat(cvs.current(candidateId)).contains(old);
        assertThat(records.count()).isEqualTo(1);
        assertThat(storage.objects).containsOnlyKeys(oldObjectKey);
        assertThat(cvs.download(candidateId).bytes()).isEqualTo("%PDF-1.4 old".getBytes());
    }

    @Test void databaseFlushFailureCompensatesUploadAndLeavesFormerFileUnchanged() {
        var old = cvs.store(candidateId, validation.prepare("old.pdf", "application/pdf", "%PDF-1.4 old".getBytes()), "staff", checkedAt);
        String oldObjectKey = records.findById(old.id()).orElseThrow().objectKey;
        assertThatThrownBy(() -> cvs.store(candidateId,
                validation.prepare("new.pdf", "application/pdf", "%PDF-1.4 new".getBytes()), "x".repeat(256), checkedAt))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(cvs.current(candidateId)).contains(old);
        assertThat(records.count()).isEqualTo(1);
        assertThat(storage.objects).containsOnlyKeys(oldObjectKey);
        assertThat(cvs.download(candidateId).bytes()).isEqualTo("%PDF-1.4 old".getBytes());
    }

    @TestConfiguration
    static class StorageConfiguration {
        @Bean MemoryStorage objectStorage() { return new MemoryStorage(); }
    }

    static class MemoryStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        @Override public void put(String key, byte[] content, String mime) { objects.put(key, content.clone()); }
        @Override public byte[] get(String key) { return objects.get(key).clone(); }
        @Override public void delete(String key) { objects.remove(key); }
    }
}
