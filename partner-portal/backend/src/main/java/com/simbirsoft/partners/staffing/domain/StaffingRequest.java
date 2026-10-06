package com.simbirsoft.partners.staffing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record StaffingRequest(UUID id, UUID ownerProfileId, UUID leadId, String number, UUID idempotencyKey,
        RoleRequirements role, WorkConditions work, SelectionRequirements selection, StaffingRequestStatus status,
        Instant createdAt, Instant updatedAt, long version, String assignedResourceManagerUsername) {

    public StaffingRequest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerProfileId, "ownerProfileId is required");
        Objects.requireNonNull(leadId, "leadId is required");
        number = requiredText(number, 32, "number");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(work, "work is required");
        Objects.requireNonNull(selection, "selection is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt) || version < 0) throw new IllegalArgumentException("Invalid request version or dates");
    }

    public record RoleRequirements(String title, int headcount, String grade, List<String> requiredSkills,
            List<String> preferredSkills, String taskDescription) {
        public RoleRequirements {
            title = requiredText(title, 200, "title");
            grade = requiredText(grade, 80, "grade");
            taskDescription = requiredText(taskDescription, 10000, "taskDescription");
            if (headcount < 1 || headcount > 100) throw new IllegalArgumentException("Headcount must be between 1 and 100");
            requiredSkills = normalizedSkills(requiredSkills, false);
            if (requiredSkills.isEmpty()) throw new IllegalArgumentException("At least one required skill is required");
            preferredSkills = normalizedSkills(preferredSkills, true);
        }
    }

    public record WorkConditions(int hoursPerWeek, WorkMode mode, String location, boolean workOutsideRussiaAllowed,
            LocalDate desiredStartDate, boolean startDateFlexible) {
        public WorkConditions {
            if (hoursPerWeek < 1 || hoursPerWeek > 40) throw new IllegalArgumentException("Hours must be between 1 and 40");
            Objects.requireNonNull(mode, "work mode is required");
            location = requiredText(location, 200, "location");
            if (startDateFlexible == (desiredStartDate != null))
                throw new IllegalArgumentException("A fixed date is required unless the start date is flexible");
        }
    }

    public enum WorkMode { REMOTE, HYBRID, OFFICE }

    public record SelectionRequirements(String language, String languageLevel, boolean interviewRequired,
            boolean screeningRequired, boolean cvRequired) {
        public SelectionRequirements {
            if ((language == null || language.isBlank()) != (languageLevel == null || languageLevel.isBlank()))
                throw new IllegalArgumentException("Language and language level must be provided together");
            language = language == null || language.isBlank() ? null : requiredText(language, 80, "language");
            languageLevel = languageLevel == null || languageLevel.isBlank()
                    ? null : requiredText(languageLevel, 80, "languageLevel");
        }
    }

    public static StaffingRequest create(UUID id, UUID ownerProfileId, UUID leadId, String number,
            UUID idempotencyKey, RoleRequirements role, WorkConditions work,
            SelectionRequirements selection, Instant createdAt) {
        return new StaffingRequest(id, ownerProfileId, leadId, number, idempotencyKey, role, work, selection,
                StaffingRequestStatus.NEW, createdAt, createdAt, 0, null);
    }

    public StaffingRequest withAssignee(String username, Instant changedAt) {
        return new StaffingRequest(id, ownerProfileId, leadId, number, idempotencyKey, role, work, selection,
                status, createdAt, changedAt, version, username);
    }

    private static String requiredText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength)
            throw new IllegalArgumentException(name + " is invalid");
        return value.trim();
    }

    private static List<String> normalizedSkills(List<String> values, boolean allowEmpty) {
        if (values == null) {
            if (allowEmpty) return List.of();
            throw new IllegalArgumentException("Required skills are required");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String normalized = requiredText(value, 120, "skill");
            unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        if (unique.size() > 40) throw new IllegalArgumentException("No more than 40 skills are allowed");
        return List.copyOf(new ArrayList<>(unique.values()));
    }
}
