package com.simbirsoft.partners.staffing.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
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

@Service
@Profile("!auth-test")
public class StaffingRequestService {
    private final StaffingRequestStore requests;
    private final LeadPartnerContextLookup leads;
    private final PartnerProfileService profiles;

    public StaffingRequestService(StaffingRequestStore requests, LeadPartnerContextLookup leads,
            PartnerProfileService profiles) {
        this.requests=requests; this.leads=leads; this.profiles=profiles;
    }

    @Transactional
    public StaffingRequest create(PartnerPrincipal principal, UUID idempotencyKey, CreateCommand command) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner session required");
        if (idempotencyKey == null || command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request data is incomplete");
        UUID owner=profiles.current(principal).id();
        var replay=requests.findByIdempotencyKey(owner, idempotencyKey);
        if (replay.isPresent()) {
            StaffingRequest existing=replay.get();
            if (!existing.leadId().equals(command.leadId()) || !existing.role().equals(command.role())
                    || !existing.work().equals(command.work()) || !existing.selection().equals(command.selection()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for another request");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
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
