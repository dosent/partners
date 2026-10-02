package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.localization.application.PublishedRegistrationContentLookup;
import com.simbirsoft.partners.partner.domain.LegalConsent;
import com.simbirsoft.partners.partner.domain.InvalidTaxIdentifierException;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationRecord;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationStore;
import com.simbirsoft.partners.partner.infrastructure.RegistrationDataProtector;
import com.simbirsoft.partners.shared.api.FieldValidationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PartnerApplicationSubmissionService {
    private final PartnerApplicationStore store;
    private final RegistrationDataProtector protector;
    private final PublishedRegistrationContentLookup contentLookup;

    public PartnerApplicationSubmissionService(PartnerApplicationStore store,
            RegistrationDataProtector protector, PublishedRegistrationContentLookup contentLookup) {
        this.store = store;
        this.protector = protector;
        this.contentLookup = contentLookup;
    }

    @Transactional
    public OwnerStatus submit(PartnerPrincipal owner, PartnerApplicationSubmission submission) {
        requirePartner(owner);
        var existing = store.findByIdentityProviderAndExternalSubject(owner.provider(), owner.subject());
        if (existing.isPresent()) {
            return ownerStatus(existing.get());
        }

        var registration = registrationData(submission);
        var consent = consent(submission);
        Instant submittedAt = Instant.now();
        var application = PartnerApplication.submit(UUID.randomUUID(), owner.provider(), owner.subject(),
                registration, consent, submittedAt);
        try {
            var saved = store.saveAndFlush(PartnerApplicationRecord.from(application, protector));
            return ownerStatus(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isIdentityConflict(exception)) {
                // The identity unique constraint resolves simultaneous first submissions without revealing the winner.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Application state changed; reload your status", exception);
            }
            throw exception;
        }
    }

    @Transactional
    public OwnerStatus resubmit(PartnerPrincipal owner, PartnerApplicationSubmission submission) {
        requirePartner(owner);
        var record = store.findByIdentityProviderAndExternalSubject(owner.provider(), owner.subject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        var existing = record.toDomain(protector);
        if (existing.status() != PartnerApplicationStatus.REJECTED
                && existing.status() != PartnerApplicationStatus.CHANGES_REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Application cannot be resubmitted in its current state");
        }

        var registration = registrationData(submission);
        var consent = consent(submission);
        var resubmitted = existing.resubmit(registration, consent, Instant.now());
        record.updateFrom(resubmitted, protector);
        try {
            return ownerStatus(store.saveAndFlush(record));
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Application state changed; reload your status");
        }
    }

    private PartnerRegistrationData registrationData(PartnerApplicationSubmission submission) {
        try {
            return new PartnerRegistrationData(submission.partnerType(), submission.fullName(), submission.email(),
                    submission.phone(), submission.countryCode(), submission.region(), submission.companyName(),
                    submission.taxIdentifier());
        } catch (InvalidTaxIdentifierException exception) {
            throw new FieldValidationException("INVALID_TAX_IDENTIFIER", "taxIdentifier",
                    "Номер не соответствует формату ИНН/БИН для выбранной страны.");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid registration data");
        }
    }

    private LegalConsent consent(PartnerApplicationSubmission submission) {
        if (!submission.offerAccepted() || !submission.privacyAccepted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both required consents must be accepted");
        }
        String requestedLocale = normalizeLocale(submission.locale());
        var published = contentLookup.findPublished(requestedLocale)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Registration is temporarily unavailable for this language"));
        if (!requestedLocale.equals(normalizeLocale(published.locale()))
                || submission.contentVersion() != published.contentVersion()
                || !Objects.equals(published.offerVersion(), submission.offerVersion())
                || !Objects.equals(published.privacyNoticeVersion(), submission.privacyNoticeVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registration documents have changed; reload the registration form");
        }
        Instant acceptedAt = Instant.now();
        return new LegalConsent(published.locale(), published.offerVersion(), acceptedAt,
                published.privacyNoticeVersion(), acceptedAt, published.contentVersion(), published.contentSha256());
    }

    private static boolean isIdentityConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_partner_application_identity".equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String normalizeLocale(String locale) {
        return locale == null ? "" : locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static void requirePartner(PartnerPrincipal owner) {
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Partner session is required");
        }
    }

    private static OwnerStatus ownerStatus(PartnerApplicationRecord record) {
        var history = record.getStatusHistory();
        String latestReason = history.isEmpty() ? null : history.get(history.size() - 1).toDomain().reason();
        var revisions = record.getRevisions();
        String locale = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1).getConsents().stream()
                .findFirst().map(com.simbirsoft.partners.partner.infrastructure.PartnerApplicationConsentRecord::getLocale)
                .orElse(null);
        return new OwnerStatus(record.getStatus(), latestReason, locale);
    }

    public record OwnerStatus(PartnerApplicationStatus status, String latestDecisionReason, String locale) { }
}
