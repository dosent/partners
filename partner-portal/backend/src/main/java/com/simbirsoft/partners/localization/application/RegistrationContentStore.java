package com.simbirsoft.partners.localization.application;

import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.RegistrationLocale;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Порт хранения черновиков и неизменяемой истории публикаций. */
public interface RegistrationContentStore {
    Optional<DraftState> findDraft(RegistrationLocale locale);
    DraftState saveDraft(RegistrationLocale locale, RegistrationContentDraft content,
            long expectedDraftVersion, String actor, Instant changedAt);
    DraftState claimDraftForPublication(RegistrationLocale locale, long expectedDraftVersion,
            String actor, Instant publishedAt);
    int nextPublicationVersion(RegistrationLocale locale);
    PublishedState appendPublication(RegistrationLocale locale, int version, RegistrationContentDraft content,
            String checksum, String actor, Instant publishedAt);
    void setCurrentPublication(RegistrationLocale locale, UUID publicationId);
    Optional<PublishedState> findCurrentPublication(RegistrationLocale locale);

    record DraftState(RegistrationContentDraft content, long draftVersion,
                      String updatedBy, Instant updatedAt) { }

    record PublishedState(UUID id, RegistrationLocale locale, int contentVersion,
            RegistrationContentDraft content, String checksum, String publishedBy,
            Instant publishedAt) { }
}
