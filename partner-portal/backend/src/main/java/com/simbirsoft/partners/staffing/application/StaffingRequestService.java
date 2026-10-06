package com.simbirsoft.partners.staffing.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleLookup;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import java.util.Objects;
import java.util.Locale;
import com.simbirsoft.partners.lead.application.LeadPartnerContextLookup;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import com.simbirsoft.partners.shared.api.FieldValidationException;

@Service
@Profile("!auth-test")
public class StaffingRequestService {
    private final StaffingRequestStore requests;
    private final LeadPartnerContextLookup leads;
    private final PartnerProfileService profiles;
    private final StaffRoleLookup staffRoles;
    private final StaffingRequestAssignmentAudit audit;

    public StaffingRequestService(StaffingRequestStore requests, LeadPartnerContextLookup leads,
            PartnerProfileService profiles, StaffRoleLookup staffRoles, StaffingRequestAssignmentAudit audit) {
        this.requests=requests; this.leads=leads; this.profiles=profiles;
        this.staffRoles=staffRoles; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public StaffingRequestStore.StaffPage staffPage(StaffPrincipal actor, int page, int size) {
        requireStaffPermission(actor);
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return requests.findStaffPage(page, size);
    }

    @Transactional(readOnly=true)
    public List<String> assignees(StaffPrincipal actor) {
        requireStaffPermission(actor);
        return staffRoles.usernamesWithRole(StaffRole.RESOURCE_MANAGER);
    }

    @Transactional
    public StaffingRequest assignResourceManager(StaffPrincipal actor, UUID id, String username, long expectedVersion) {
        requireStaffPermission(actor);
        if (expectedVersion < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version");
        StaffingRequest current = requests.findAny(id).orElseThrow(StaffingRequestService::notFound);
        if (current.version() != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staffing request was changed");
        String normalized = normalizeUsername(username);
        if (normalized != null && !staffRoles.hasRole(normalized, StaffRole.RESOURCE_MANAGER))
            throw new FieldValidationException("VALIDATION_ERROR", "username", "Resource manager is required");
        String previous = current.assignedResourceManagerUsername();
        if (Objects.equals(previous, normalized)) return current;
        Instant now = Instant.now();
        StaffingRequest saved = requests.saveAndFlush(current.withAssignee(normalized, now));
        var action = normalized == null ? StaffingRequestAssignmentAudit.Action.UNASSIGNED
                : previous == null ? StaffingRequestAssignmentAudit.Action.ASSIGNED : StaffingRequestAssignmentAudit.Action.REASSIGNED;
        audit.record(id, action, previous, normalized, actor.username(), now);
        return saved;
    }

    private void requireStaffPermission(StaffPrincipal actor) {
        if (actor == null || actor.username() == null || actor.username().isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff session required");
        if (!staffRoles.hasPermission(actor.username(), "staffing.request.manage"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staffing request permission required");
    }

    private static String normalizeUsername(String username) {
        if (username == null) return null;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 255 || normalized.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource manager username");
        return normalized;
    }

    public StaffingRequest create(PartnerPrincipal principal, UUID idempotencyKey, CreateCommand command) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner session required");
        if (idempotencyKey == null || command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request data is incomplete");
        UUID owner=profiles.current(principal).id();
        var replay=requests.findByIdempotencyKey(owner, idempotencyKey);
        if (replay.isPresent()) {
            StaffingRequest existing=replay.get();
            if (!matches(existing, command)) throw conflict();
            return existing;
        }
        leads.findOwnedContext(command.leadId(), owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        try {
            UUID id=UUID.randomUUID();
            return requests.save(StaffingRequest.create(id, owner, command.leadId(),
                    "SR-"+id.toString().substring(0, 8).toUpperCase(), idempotencyKey,
                    command.role(), command.work(), command.selection(), Instant.now()));
        } catch (IllegalArgumentException invalid) {
            throw validation(invalid.getMessage());
        } catch (DataIntegrityViolationException duplicate) {
            var winner = requests.findByIdempotencyKey(owner, idempotencyKey);
            if (winner.isPresent() && matches(winner.get(), command)) return winner.get();
            if (winner.isPresent()) throw conflict();
            throw duplicate;
        }
    }

    private static boolean matches(StaffingRequest request, CreateCommand command) {
        return request.leadId().equals(command.leadId()) && request.role().equals(command.role())
                && request.work().equals(command.work()) && request.selection().equals(command.selection());
    }
    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for another request");
    }
    private static RuntimeException validation(String message) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        String field = normalized.contains("taskdescription") ? "role.taskDescription"
                : normalized.contains("preferred") ? "role.preferredSkills"
                : normalized.contains("grade") ? "role.grade"
                : normalized.contains("required skills") || normalized.contains("skill") ? "role.requiredSkills"
                : normalized.contains("headcount") ? "role.headcount"
                : normalized.contains("hours") ? "work.hoursPerWeek"
                : normalized.contains("location") ? "work.location"
                : normalized.contains("date") ? "work.desiredStartDate"
                : normalized.contains("language") ? "selection.languageLevel" : "role.title";
        return new FieldValidationException("VALIDATION_ERROR", field, "Invalid value");
    }

    @Transactional(readOnly=true)
    public List<StaffingRequest> list(PartnerPrincipal principal, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return requests.findOwnedPage(owner(principal), page, size);
    }

    @Transactional(readOnly=true)
    public StaffingRequest get(PartnerPrincipal principal, UUID id) {
        return requests.findOwned(id, owner(principal)).orElseThrow(StaffingRequestService::notFound);
    }

    @Transactional(readOnly=true)
    public UUID ownerId(PartnerPrincipal principal) {
        return owner(principal);
    }

    private UUID owner(PartnerPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner session required");
        return profiles.current(principal).id();
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Staffing request not found"); }
    public record CreateCommand(UUID leadId, StaffingRequest.RoleRequirements role, StaffingRequest.WorkConditions work,
            StaffingRequest.SelectionRequirements selection) { }
}
