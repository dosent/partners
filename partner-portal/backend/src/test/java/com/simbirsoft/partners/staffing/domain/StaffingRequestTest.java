package com.simbirsoft.partners.staffing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffingRequestTest {
    @Test
    void staffingRequestAggregateIsAvailableInTheStaffingDomain() {
        boolean present;
        try {
            present = Class.forName("com.simbirsoft.partners.staffing.domain.StaffingRequest") != null;
        } catch (ClassNotFoundException missing) {
            present = false;
        }
        assertThat(present).isTrue();
    }

    @Test
    void rejectsHeadcountOutsideSupportedRange() {
        assertThatThrownBy(() -> role(0, List.of("Java")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> role(101, List.of("Java")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRequiredSkills() {
        assertThatThrownBy(() -> role(1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flexibleStartDateMustNotCarryFixedDateAndFixedDateMustBePresent() {
        assertThatThrownBy(() -> new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE,
                "Россия", false, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE,
                "Россия", false, LocalDate.parse("2026-11-01"), true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void languageAndLevelMustBeProvidedTogether() {
        assertThatThrownBy(() -> new StaffingRequest.SelectionRequirements("English", null, true, false, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffingRequest.SelectionRequirements(null, "B1", true, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creationStartsInNewStateAndNormalizesSkillLists() {
        var request = StaffingRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SR-12345678",
                UUID.randomUUID(), role(1, List.of(" Java ", "Spring")),
                new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE, "Россия", false,
                        null, true),
                new StaffingRequest.SelectionRequirements(null, null, true, false, true),
                Instant.parse("2026-10-05T00:00:00Z"));

        assertThat(request.status()).isEqualTo(StaffingRequestStatus.NEW);
        assertThat(request.assignedResourceManagerUsername()).isNull();
        assertThat(request.role().requiredSkills()).containsExactly("Java", "Spring");

        Instant changedAt = Instant.parse("2026-10-06T00:00:00Z");
        var assigned = request.withAssignee("resource.manager", changedAt);
        assertThat(assigned.assignedResourceManagerUsername()).isEqualTo("resource.manager");
        assertThat(assigned.updatedAt()).isEqualTo(changedAt);
        assertThat(assigned.id()).isEqualTo(request.id());
        assertThat(assigned.version()).isEqualTo(request.version());
        assertThat(request.assignedResourceManagerUsername()).isNull();
        assertThat(assigned.withAssignee(null, changedAt).assignedResourceManagerUsername()).isNull();
    }

    private static StaffingRequest.RoleRequirements role(int headcount, List<String> skills) {
        return new StaffingRequest.RoleRequirements("Java-разработчик", headcount, "Middle", skills,
                List.of(), "Разработка сервисов");
    }
}
