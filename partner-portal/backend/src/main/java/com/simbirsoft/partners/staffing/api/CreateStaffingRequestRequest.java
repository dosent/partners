package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.staffing.application.StaffingRequestService;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    public record Role(String title, int headcount, String grade, List<String> requiredSkills,
            List<String> preferredSkills, String taskDescription) { }
    public record Work(int hoursPerWeek, StaffingRequest.WorkMode mode, String location,
            boolean workOutsideRussiaAllowed, java.time.LocalDate desiredStartDate, boolean startDateFlexible) { }
    public record Selection(String language, String languageLevel, boolean interviewRequired,
            boolean screeningRequired, boolean cvRequired) { }
}
