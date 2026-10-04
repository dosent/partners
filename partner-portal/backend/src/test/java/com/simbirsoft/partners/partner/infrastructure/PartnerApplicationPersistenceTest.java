package com.simbirsoft.partners.partner.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.partner.domain.LegalConsent;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.domain.PartnerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "partners.registration.encryption-key=" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "partners.registration.hash-key=" + "dGVzdC1oYXNoLWtleS10aGF0LWlzLWxvbmctZW5vdWdo"
})
class PartnerApplicationPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    PartnerApplicationStore store;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void persistsEncryptedRevisionConsentVersionsAndAppendOnlyStatusHistory() {
        PartnerApplication domain = submittedApplication();
        PartnerApplicationRecord record = PartnerApplicationRecord.from(domain,
                new RegistrationDataProtector(new ObjectMapper(), encryptionKey(), hashKey()));

        store.saveAndFlush(record);
        entityManager.clear();

        PartnerApplicationRecord restored = store.findById(domain.id()).orElseThrow();
        assertThat(restored.getRevisions()).hasSize(1);
        PartnerApplicationRevisionRecord revision = restored.getRevisions().getFirst();
        assertThat(new String(revision.getEncryptedPayload(), StandardCharsets.UTF_8))
                .doesNotContain("Иван Иванов", "ivan@example.test", "1234567890");
        assertThat(revision.getConsents()).extracting(PartnerApplicationConsentRecord::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.OFFER, ConsentType.PERSONAL_DATA_PROCESSING);
        assertThat(restored.getStatusHistory()).hasSize(1);
        assertThat(restored.getVersion()).isEqualTo(0L);
        assertThat(restored.toDomain(new RegistrationDataProtector(
                new ObjectMapper(), encryptionKey(), hashKey())).revisions().getFirst().registrationData())
                .isEqualTo(domain.revisions().getFirst().registrationData());
    }

    @Test
    void optimisticLockRejectsStaleStatusUpdate() {
        RegistrationDataProtector protector = new RegistrationDataProtector(
                new ObjectMapper(), encryptionKey(), hashKey());
        PartnerApplicationRecord original = PartnerApplicationRecord.from(submittedApplication(), protector);
        store.saveAndFlush(original);
        entityManager.detach(original);
        PartnerApplicationRecord firstCopy = store.findById(original.getId()).orElseThrow();
        firstCopy.getStatusHistory().size();
        firstCopy.getRevisions().size();
        firstCopy.getRevisions().forEach(revision -> revision.getConsents().size());
        entityManager.detach(firstCopy);
        PartnerApplicationRecord staleCopy = store.findById(original.getId()).orElseThrow();
        staleCopy.getStatusHistory().size();
        staleCopy.getRevisions().size();
        staleCopy.getRevisions().forEach(revision -> revision.getConsents().size());
        entityManager.detach(staleCopy);

        firstCopy.updateFrom(firstCopy.toDomain(protector)
                .changeStatus(PartnerApplicationStatus.APPROVED, "staff-1", "", NOW.plusSeconds(5)),
                protector);
        store.saveAndFlush(firstCopy);
        staleCopy.updateFrom(staleCopy.toDomain(protector)
                .changeStatus(PartnerApplicationStatus.REJECTED, "staff-2", "Уточнить данные", NOW.plusSeconds(6)),
                protector);

        assertThatExceptionOfType(ObjectOptimisticLockingFailureException.class)
                .isThrownBy(() -> store.saveAndFlush(staleCopy));
    }

    @Test
    void oneExternalIdentityCannotCreateDuplicateApplicationRoots() {
        store.saveAndFlush(PartnerApplicationRecord.from(submittedApplication(),
                new RegistrationDataProtector(new ObjectMapper(), encryptionKey(), hashKey())));
        PartnerApplication duplicate = PartnerApplication.submit(UUID.randomUUID(), "yandex", "subject-1",
                registrationData(), consent(), NOW);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> store.saveAndFlush(PartnerApplicationRecord.from(duplicate,
                        new RegistrationDataProtector(new ObjectMapper(), encryptionKey(), hashKey()))));
    }

    @Test
    void resubmissionAppendsNewRevisionAndStatusEventsWithoutReinsertingHistory() {
        RegistrationDataProtector protector = new RegistrationDataProtector(
                new ObjectMapper(), encryptionKey(), hashKey());
        PartnerApplication firstSubmission = submittedApplication();
        store.saveAndFlush(PartnerApplicationRecord.from(firstSubmission, protector));
        entityManager.clear();

        PartnerApplicationRecord record = store.findById(firstSubmission.id()).orElseThrow();
        PartnerApplication resubmitted = record.toDomain(protector)
                .changeStatus(PartnerApplicationStatus.REJECTED, "staff-1", "Исправить реквизиты", NOW.plusSeconds(1))
                .resubmit(registrationData(), consent(), NOW.plusSeconds(2));
        record.updateFrom(resubmitted, protector);

        store.saveAndFlush(record);
        entityManager.clear();

        PartnerApplicationRecord restored = store.findById(firstSubmission.id()).orElseThrow();
        assertThat(restored.getRevisions()).extracting(PartnerApplicationRevisionRecord::getRevisionNumber)
                .containsExactly(1, 2);
        assertThat(restored.getStatusHistory()).hasSize(3);
        assertThat(restored.toDomain(protector).status()).isEqualTo(PartnerApplicationStatus.MANUAL_REVIEW);
    }

    private static PartnerApplication submittedApplication() {
        return PartnerApplication.submit(UUID.randomUUID(), "yandex", "subject-1",
                registrationData(), consent(), NOW);
    }

    private static PartnerRegistrationData registrationData() {
        return new PartnerRegistrationData(PartnerType.LEGAL_ENTITY, "Иван Иванов", "ivan@example.test",
                "+70000000000", "RU", "Самарская область", "ООО Пример", "1000000002");
    }

    private static LegalConsent consent() {
        return new LegalConsent("RU", "offer-3", NOW, "privacy-5", NOW);
    }

    private static String encryptionKey() {
        return "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    }

    private static String hashKey() {
        return "dGVzdC1oYXNoLWtleS10aGF0LWlzLWxvbmctZW5vdWdo";
    }
}
