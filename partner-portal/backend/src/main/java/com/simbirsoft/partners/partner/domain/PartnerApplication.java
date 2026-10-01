package com.simbirsoft.partners.partner.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PartnerApplication {
    private final UUID id;
    private final String identityProvider;
    private final String externalSubject;
    private final PartnerApplicationStatus status;
    private final List<PartnerApplicationRevision> revisions;
    private final List<PartnerApplicationStatusEvent> statusHistory;

    private PartnerApplication(
            UUID id,
            String identityProvider,
            String externalSubject,
            PartnerApplicationStatus status,
            List<PartnerApplicationRevision> revisions,
            List<PartnerApplicationStatusEvent> statusHistory) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.identityProvider = requireText(identityProvider, "identityProvider");
        this.externalSubject = requireText(externalSubject, "externalSubject");
        this.status = Objects.requireNonNull(status, "status is required");
        this.revisions = List.copyOf(revisions);
        this.statusHistory = List.copyOf(statusHistory);
    }

    public static PartnerApplication submit(
            UUID id,
            String identityProvider,
            String externalSubject,
            PartnerRegistrationData registrationData,
            LegalConsent consent,
            Instant submittedAt) {
        var revision = new PartnerApplicationRevision(1, registrationData, consent, submittedAt);
        var initialStatus = new PartnerApplicationStatusEvent(
                PartnerApplicationStatus.PENDING_REVIEW, "PARTNER", null, submittedAt);
        return new PartnerApplication(id, identityProvider, externalSubject,
                PartnerApplicationStatus.PENDING_REVIEW, List.of(revision), List.of(initialStatus));
    }

    public static PartnerApplication restore(
            UUID id,
            String identityProvider,
            String externalSubject,
            PartnerApplicationStatus status,
            List<PartnerApplicationRevision> revisions,
            List<PartnerApplicationStatusEvent> statusHistory) {
        if (revisions.isEmpty() || statusHistory.isEmpty()) {
            throw new IllegalArgumentException("An application must have revision and status history");
        }
        for (int index = 0; index < revisions.size(); index++) {
            if (revisions.get(index).revisionNumber() != index + 1) {
                throw new IllegalArgumentException("Application revision numbers must be continuous");
            }
        }
        if (statusHistory.getLast().status() != status) {
            throw new IllegalArgumentException("Current status must match the last history event");
        }
        return new PartnerApplication(id, identityProvider, externalSubject,
                status, revisions, statusHistory);
    }

    public PartnerApplication changeStatus(
            PartnerApplicationStatus nextStatus, String changedBy, String reason, Instant changedAt) {
        if (status == PartnerApplicationStatus.APPROVED || status == PartnerApplicationStatus.REJECTED) {
            throw new IllegalStateException("A terminal application must be resubmitted before status can change");
        }
        var event = new PartnerApplicationStatusEvent(nextStatus, changedBy, reason, changedAt);
        var history = new ArrayList<>(statusHistory);
        history.add(event);
        return new PartnerApplication(id, identityProvider, externalSubject,
                nextStatus, revisions, history);
    }

    public PartnerApplication resubmit(
            PartnerRegistrationData registrationData, LegalConsent consent, Instant submittedAt) {
        if (status != PartnerApplicationStatus.REJECTED
                && status != PartnerApplicationStatus.CHANGES_REQUESTED) {
            throw new IllegalStateException("Only rejected or returned applications can be resubmitted");
        }
        var nextRevision = new PartnerApplicationRevision(revisions.size() + 1,
                registrationData, consent, submittedAt);
        var revisionHistory = new ArrayList<>(revisions);
        revisionHistory.add(nextRevision);
        var statusHistoryCopy = new ArrayList<>(statusHistory);
        statusHistoryCopy.add(new PartnerApplicationStatusEvent(
                PartnerApplicationStatus.PENDING_REVIEW, "PARTNER", null, submittedAt));
        return new PartnerApplication(id, identityProvider, externalSubject,
                PartnerApplicationStatus.PENDING_REVIEW, revisionHistory, statusHistoryCopy);
    }

    public UUID id() {
        return id;
    }

    public String identityProvider() {
        return identityProvider;
    }

    public String externalSubject() {
        return externalSubject;
    }

    public PartnerApplicationStatus status() {
        return status;
    }

    public List<PartnerApplicationRevision> revisions() {
        return revisions;
    }

    public List<PartnerApplicationStatusEvent> statusHistory() {
        return statusHistory;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
