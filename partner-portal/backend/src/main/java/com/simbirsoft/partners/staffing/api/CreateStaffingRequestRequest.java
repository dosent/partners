package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.staffing.application.StaffingRequestService;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateStaffingRequestRequest(@NotNull UUID leadId, @NotNull @Valid Role role,
        @NotNull @Valid Work work, @NotNull @Valid Selection selection) {
    public StaffingRequestService.CreateCommand command() {
        return new StaffingRequestService.CreateCommand(leadId,
                new StaffingRequest.RoleRequirements(role.title(), role.headcount(), role.grade(), role.requiredSkills(),
                        role.preferredSkills(), role.taskDescription()),
                new StaffingRequest.WorkConditions(work.hoursPerWeek(), work.mode(), work.location(),
                        work.workOutsideRussiaAllowed(), work.desiredStartDate(), work.startDateFlexible()),
                new StaffingRequest.SelectionRequirements(selection.language(), selection.languageLevel(),
                        selection.interviewRequired(), selection.screeningRequired(), selection.cvRequired()));
    }
    public record Role(@NotBlank @Size(max=200) String title, @Min(1) @Max(100) int headcount,
            @NotBlank @Size(max=80) String grade, @NotEmpty @Size(max=40) List<@NotBlank @Size(max=120) String> requiredSkills,
            @Size(max=40) List<@NotBlank @Size(max=120) String> preferredSkills, @NotBlank @Size(max=10000) String taskDescription) { }
    public record Work(@Min(1) @Max(40) int hoursPerWeek, @NotNull StaffingRequest.WorkMode mode, @NotBlank @Size(max=200) String location,
            boolean workOutsideRussiaAllowed, java.time.LocalDate desiredStartDate, boolean startDateFlexible) { }
    public record Selection(String language, String languageLevel, @NotNull Boolean interviewRequired,
            @NotNull Boolean screeningRequired, @NotNull Boolean cvRequired) { }
}
