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
    private final String assignedManagerUsername;
    private final List<PartnerApplicationRevision> revisions;
    private final List<PartnerApplicationStatusEvent> statusHistory;

    private PartnerApplication(
            UUID id,
            String identityProvider,
            String externalSubject,
            PartnerApplicationStatus status,
            String assignedManagerUsername,
            List<PartnerApplicationRevision> revisions,
            List<PartnerApplicationStatusEvent> statusHistory) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.identityProvider = requireText(identityProvider, "identityProvider");
        this.externalSubject = requireText(externalSubject, "externalSubject");
        this.status = Objects.requireNonNull(status, "status is required");
        this.assignedManagerUsername = assignedManagerUsername;
        if (assignedManagerUsername != null && status != PartnerApplicationStatus.APPROVED) {
            throw new IllegalArgumentException("A manager can only be assigned to an approved application");
        }
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
                PartnerApplicationStatus.MANUAL_REVIEW, "PARTNER", null, submittedAt);
        return new PartnerApplication(id, identityProvider, externalSubject,
                PartnerApplicationStatus.MANUAL_REVIEW, null, List.of(revision), List.of(initialStatus));
    }

    public static PartnerApplication restore(
            UUID id,
            String identityProvider,
            String externalSubject,
            PartnerApplicationStatus status,
            List<PartnerApplicationRevision> revisions,
            List<PartnerApplicationStatusEvent> statusHistory) {
        return restore(id, identityProvider, externalSubject, status, null, revisions, statusHistory);
    }

    public static PartnerApplication restore(
            UUID id,
            String identityProvider,
            String externalSubject,
            PartnerApplicationStatus status,
            String assignedManagerUsername,
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
                status, assignedManagerUsername, revisions, statusHistory);
    }

    public PartnerApplication changeStatus(
            PartnerApplicationStatus nextStatus, String changedBy, String reason, Instant changedAt) {
        boolean allowed = switch (status) {
            case PENDING_REVIEW -> nextStatus == PartnerApplicationStatus.MANUAL_REVIEW
                    || nextStatus == PartnerApplicationStatus.CHANGES_REQUESTED
                    || nextStatus == PartnerApplicationStatus.REJECTED
                    || nextStatus == PartnerApplicationStatus.APPROVED;
            case MANUAL_REVIEW -> nextStatus == PartnerApplicationStatus.CHANGES_REQUESTED
                    || nextStatus == PartnerApplicationStatus.REJECTED
                    || nextStatus == PartnerApplicationStatus.APPROVED;
            case CHANGES_REQUESTED, REJECTED, APPROVED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Status transition is not allowed: " + status + " -> " + nextStatus);
        }
        var history = new ArrayList<>(statusHistory);
        history.add(new PartnerApplicationStatusEvent(nextStatus, changedBy, reason, changedAt));
        return new PartnerApplication(id, identityProvider, externalSubject,
                nextStatus, nextStatus == PartnerApplicationStatus.APPROVED ? assignedManagerUsername : null,
                revisions, history);
    }

    public PartnerApplication decide(PartnerApplicationStatus nextStatus, String changedBy, String reason,
                                     String managerUsername, Instant changedAt) {
        if (status != PartnerApplicationStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("Only applications under manual review can be decided");
        }
        if (nextStatus != PartnerApplicationStatus.APPROVED
                && nextStatus != PartnerApplicationStatus.REJECTED
                && nextStatus != PartnerApplicationStatus.CHANGES_REQUESTED) {
            throw new IllegalArgumentException("Unsupported moderation decision");
        }
        String normalizedReason = reason == null ? null : reason.trim();
        if (nextStatus != PartnerApplicationStatus.APPROVED) {
            if (normalizedReason == null || normalizedReason.isEmpty() || normalizedReason.length() > 1000
                    || normalizedReason.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("A safe decision reason is required");
            }
        }
        String normalizedManager = managerUsername == null ? null : managerUsername.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedManager != null && (normalizedManager.isBlank() || nextStatus != PartnerApplicationStatus.APPROVED)) {
            throw new IllegalArgumentException("A manager can only be assigned during approval");
        }
        var event = new PartnerApplicationStatusEvent(nextStatus, changedBy,
                normalizedReason == null || normalizedReason.isEmpty() ? null : normalizedReason, changedAt);
        var history = new ArrayList<>(statusHistory);
        history.add(event);
        return new PartnerApplication(id, identityProvider, externalSubject,
                nextStatus, normalizedManager, revisions, history);
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
                PartnerApplicationStatus.MANUAL_REVIEW, "PARTNER", null, submittedAt));
        return new PartnerApplication(id, identityProvider, externalSubject,
                PartnerApplicationStatus.MANUAL_REVIEW, null, revisionHistory, statusHistoryCopy);
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

    public String assignedManagerUsername() { return assignedManagerUsername; }

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
