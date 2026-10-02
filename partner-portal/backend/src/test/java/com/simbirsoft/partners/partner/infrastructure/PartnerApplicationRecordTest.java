package com.simbirsoft.partners.partner.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.partner.domain.LegalConsent;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.domain.PartnerType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerApplicationRecordTest {
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void updateFromAppendsResubmittedRevisionAndEventsWithoutDuplicatingPersistedHistory() {
        RegistrationDataProtector protector = new RegistrationDataProtector(
                new ObjectMapper(), "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "dGVzdC1oYXNoLWtleS10aGF0LWlzLWxvbmctZW5vdWdo");
        PartnerApplication submitted = PartnerApplication.submit(UUID.randomUUID(), "yandex", "subject-1",
                registrationData(), consent(), NOW);
        PartnerApplicationRecord record = PartnerApplicationRecord.from(submitted, protector);

        PartnerApplication resubmitted = record.toDomain(protector)
                .changeStatus(PartnerApplicationStatus.MANUAL_REVIEW, "staff-1", "", NOW.plusSeconds(1))
                .changeStatus(PartnerApplicationStatus.REJECTED, "staff-1", "Исправить реквизиты", NOW.plusSeconds(2))
                .resubmit(registrationData(), consent(), NOW.plusSeconds(3));
        record.updateFrom(resubmitted, protector);

        PartnerApplication restored = record.toDomain(protector);
        assertThat(restored.revisions()).extracting(revision -> revision.revisionNumber())
                .containsExactly(1, 2);
        assertThat(restored.statusHistory()).extracting(event -> event.status())
                .containsExactly(PartnerApplicationStatus.MANUAL_REVIEW,
                        PartnerApplicationStatus.MANUAL_REVIEW,
                        PartnerApplicationStatus.REJECTED,
                        PartnerApplicationStatus.MANUAL_REVIEW);
    }

    private static PartnerRegistrationData registrationData() {
        return new PartnerRegistrationData(PartnerType.LEGAL_ENTITY, "Иван Иванов", "ivan@example.test",
                "+70000000000", "RU", "Самарская область", "ООО Пример", "1234567890");
    }

    private static LegalConsent consent() {
        return new LegalConsent("RU", "offer-3", NOW, "privacy-5", NOW);
    }
}
