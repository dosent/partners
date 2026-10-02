package com.simbirsoft.partners.partner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PartnerApplicationTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void submissionStartsAtRevisionOneManualReviewAndPreservesConsent() {
        PartnerApplication application = PartnerApplication.submit(
                UUID.randomUUID(), "yandex", "subject-1", individual(), consent(), SUBMITTED_AT);

        assertThat(application.status()).isEqualTo(PartnerApplicationStatus.MANUAL_REVIEW);
        assertThat(application.revisions()).hasSize(1);
        assertThat(application.revisions().getFirst().revisionNumber()).isEqualTo(1);
        assertThat(application.revisions().getFirst().consent()).isEqualTo(consent());
        assertThat(application.statusHistory()).hasSize(1);
        assertThat(application.statusHistory().getFirst().status()).isEqualTo(PartnerApplicationStatus.MANUAL_REVIEW);
        assertThat(application.statusHistory().getFirst().changedBy()).isEqualTo("PARTNER");
    }

    @Test
    void resubmissionAfterRejectionAppendsRevisionAndKeepsPreviousRevision() {
        PartnerApplication application = PartnerApplication.submit(
                UUID.randomUUID(), "yandex", "subject-1", individual(), consent(), SUBMITTED_AT)
                .changeStatus(PartnerApplicationStatus.MANUAL_REVIEW, "staff-1", "", SUBMITTED_AT)
                .changeStatus(PartnerApplicationStatus.REJECTED, "staff-1", "Нужны уточнения", SUBMITTED_AT);

        PartnerApplication resubmitted = application.resubmit(
                individual(), consent(), SUBMITTED_AT.plusSeconds(60));

        assertThat(resubmitted.status()).isEqualTo(PartnerApplicationStatus.MANUAL_REVIEW);
        assertThat(resubmitted.revisions()).hasSize(2);
        assertThat(resubmitted.revisions()).extracting(PartnerApplicationRevision::revisionNumber)
                .containsExactly(1, 2);
        assertThat(application.revisions()).hasSize(1);
        assertThat(resubmitted.statusHistory()).hasSize(4);
    }

    @Test
    void cannotResubmitAnApplicationThatIsStillUnderReview() {
        PartnerApplication application = PartnerApplication.submit(
                UUID.randomUUID(), "yandex", "subject-1", individual(), consent(), SUBMITTED_AT);

        assertThatIllegalStateException().isThrownBy(() -> application.resubmit(
                individual(), consent(), SUBMITTED_AT.plusSeconds(60)));
    }

    @Test
    void managerCanResolvePendingApplicationDirectly() {
        PartnerApplication application = PartnerApplication.submit(
                UUID.randomUUID(), "yandex", "subject-1", individual(), consent(), SUBMITTED_AT);

        assertThat(application.changeStatus(PartnerApplicationStatus.APPROVED,
                "staff-1", "", SUBMITTED_AT.plusSeconds(1)).status())
                .isEqualTo(PartnerApplicationStatus.APPROVED);
    }

    @Test
    void changesRequestedApplicationMustBeResubmittedInsteadOfChangingReviewStatus() {
        PartnerApplication application = PartnerApplication.submit(
                UUID.randomUUID(), "yandex", "subject-1", individual(), consent(), SUBMITTED_AT)
                .changeStatus(PartnerApplicationStatus.MANUAL_REVIEW, "staff-1", "", SUBMITTED_AT.plusSeconds(1))
                .changeStatus(PartnerApplicationStatus.CHANGES_REQUESTED, "staff-1", "Уточнить данные", SUBMITTED_AT.plusSeconds(2));

        assertThatIllegalStateException().isThrownBy(() -> application.changeStatus(
                PartnerApplicationStatus.MANUAL_REVIEW, "staff-1", "", SUBMITTED_AT.plusSeconds(3)));
    }

    private static PartnerRegistrationData individual() {
        return new PartnerRegistrationData(PartnerType.INDIVIDUAL, "Иван Иванов", "ivan@example.test",
                "+70000000000", "RU", "Самарская область", null, null);
    }

    private static LegalConsent consent() {
        return new LegalConsent("RU", "offer-3", SUBMITTED_AT, "privacy-5", SUBMITTED_AT);
    }
}
