package com.simbirsoft.partners.localization.infrastructure;

import com.simbirsoft.partners.localization.application.RegistrationContentStore;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.DraftState;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.PublishedState;
import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.RegistrationLocale;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaRegistrationContentStore implements RegistrationContentStore {
    private final RegistrationContentDraftJpaRepository drafts;
    private final RegistrationContentPublicationJpaRepository publications;
    private final RegistrationContentCurrentJpaRepository current;

    JpaRegistrationContentStore(RegistrationContentDraftJpaRepository drafts,
            RegistrationContentPublicationJpaRepository publications,
            RegistrationContentCurrentJpaRepository current) {
        this.drafts = drafts;
        this.publications = publications;
        this.current = current;
    }

    @Override
    public Optional<DraftState> findDraft(RegistrationLocale locale) {
        return drafts.findByLocale(locale.value()).map(this::toDraftState);
    }

    @Override
    public DraftState saveDraft(RegistrationLocale locale, RegistrationContentDraft content,
            long expectedDraftVersion, String actor, Instant changedAt) {
        RegistrationContentDraftRecord record = drafts.findByLocale(locale.value()).orElse(null);
        if (record == null) {
            if (expectedDraftVersion != 0) throw conflict();
            record = new RegistrationContentDraftRecord(locale, content, actor, changedAt);
        } else {
            if (record.draftVersion() != expectedDraftVersion) throw conflict();
            record.replaceContent(content);
            record.updateAudit(actor, changedAt);
        }
        try {
            return toDraftState(drafts.saveAndFlush(record));
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Override
    public DraftState claimDraftForPublication(RegistrationLocale locale, long expectedDraftVersion,
            String actor, Instant publishedAt) {
        RegistrationContentDraftRecord record = drafts.findByLocale(locale.value())
                .orElseThrow(JpaRegistrationContentStore::conflict);
        if (record.draftVersion() != expectedDraftVersion) throw conflict();
        // Updating audit metadata makes Hibernate issue a version-checked UPDATE before the
        // immutable publication is appended, serializing concurrent edits and publish attempts.
        record.updateAudit(actor, publishedAt);
        try {
            return toDraftState(drafts.saveAndFlush(record));
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Override
    public int nextPublicationVersion(RegistrationLocale locale) {
        return publications.maxVersion(locale.value()) + 1;
    }

    @Override
    public PublishedState appendPublication(RegistrationLocale locale, int version,
            RegistrationContentDraft content, String checksum, String actor, Instant publishedAt) {
        return publications.saveAndFlush(new RegistrationContentPublicationRecord(locale, version, content,
                checksum, actor, publishedAt)).toState();
    }

    @Override
    public void setCurrentPublication(RegistrationLocale locale, UUID publicationId) {
        RegistrationContentPublicationRecord publication = publications.findById(publicationId)
                .orElseThrow(() -> new IllegalStateException("Registration content publication disappeared"));
        current.saveAndFlush(new RegistrationContentCurrentRecord(locale.value(), publication));
    }

    @Override
    public Optional<PublishedState> findCurrentPublication(RegistrationLocale locale) {
        return current.findById(locale.value()).map(RegistrationContentCurrentRecord::publication)
                .map(RegistrationContentPublicationRecord::toState);
    }

    private DraftState toDraftState(RegistrationContentDraftRecord record) {
        return new DraftState(record.content(), record.draftVersion(), record.updatedBy(), record.updatedAt());
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException(RegistrationContentDraftRecord.class, "draft version");
    }
}
