package com.simbirsoft.partners.lead.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadLane;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import com.simbirsoft.partners.lead.domain.LeadStatusEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("!auth-test")
public class LeadService implements LeadPartnerContextLookup {
    private final LeadStore leads;
    private final LeadPartnerIdentity partners;

    public LeadService(LeadStore leads, LeadPartnerIdentity partners) {
        this.leads = leads;
        this.partners = partners;
    }

    @Transactional
    public Lead create(PartnerPrincipal principal, CreateLead command) {
        return create(principal, command, ignored -> { });
    }

    /** Runs attachment metadata persistence inside the same database transaction as lead creation. */
    @Transactional
    public Lead create(PartnerPrincipal principal, CreateLead command, Consumer<Lead> afterPersist) {
        UUID ownerId = owner(principal);
        validate(command);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(id, ownerId, "L-" + id.toString().substring(0, 8).toUpperCase(), command.companyName().trim(),
                blankToNull(command.domainOrTaxId()), command.requestSummary().trim(), command.budgetAmount(),
                blankToNull(command.budgetCurrency()), command.plannedStartAt(), blankToNull(command.decisionMakerName()),
                blankToNull(command.decisionMakerEmail()), command.description().trim(), LeadStatus.NEW, now, now);
        Lead saved = leads.save(lead);
        afterPersist.accept(saved);
        return saved;
    }

    public UUID profileId(PartnerPrincipal principal) { return owner(principal); }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<LeadContext> findOwnedContext(UUID leadId, UUID partnerProfileId) {
        return leads.findOwned(leadId, partnerProfileId)
                .map(lead -> new LeadContext(lead.id(), lead.companyName(), lead.requestSummary()));
    }

    @Transactional(readOnly = true)
    public Board board(PartnerPrincipal principal, String query, Instant from, Instant to, LeadStatus status,
            int limit, List<String> cursors) {
        UUID ownerId = owner(principal);
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page size");
        if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        List<Column> columns = new ArrayList<>();
        LeadLane selected = status == null ? null : status.lane();
        for (LeadLane lane : LeadLane.values()) {
            if (selected != null && selected != lane) continue;
            List<LeadStatus> statuses = status == null ? lane.statuses() : List.of(status);
            String token = cursors == null || cursors.size() <= lane.ordinal() ? null : cursors.get(lane.ordinal());
            Cursor cursor = decode(token);
            long total = leads.count(ownerId, statuses, query, from, to);
            LeadStore.Page page = leads.findPage(ownerId, statuses, query, from, to,
                    cursor == null ? null : cursor.updatedAt(), cursor == null ? null : cursor.id(), limit);
            String next = page.hasMore() && !page.items().isEmpty() ? encode(page.items().getLast()) : null;
            columns.add(new Column(lane, lane.statuses(), status, total, page.items(), next));
        }
        return new Board(columns);
    }

    @Transactional
    public Lead changeStatus(UUID id, LeadStatus nextStatus, StaffPrincipal actor) {
        requireStaff(actor);
        if (nextStatus == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        Lead current = leads.findAny(id).orElseThrow(() -> notFound());
        if (!current.status().canTransitionTo(nextStatus))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lead status transition is not allowed");
        return leads.changeStatus(id, current.status(), nextStatus, actor.username(), Instant.now());
    }

    @Transactional(readOnly = true)
    public LeadStore.StaffPage staffPage(StaffPrincipal actor, LeadStatus status, String query, int page, int size) {
        requireStaff(actor);
        if (page < 0 || size < 1 || size > 100 || (query != null && query.length() > 200))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lead page request");
        return leads.findStaffPage(status, query, page, size);
    }

    @Transactional(readOnly = true)
    public StaffDetail staffDetail(StaffPrincipal actor, UUID id) {
        requireStaff(actor);
        Lead lead = leads.findAny(id).orElseThrow(LeadService::notFound);
        return new StaffDetail(lead, leads.statusHistory(id), leads.allMessages(id));
    }

    @Transactional
    public LeadMessage sendStaffMessage(StaffPrincipal actor, UUID id, String body, boolean internal) {
        requireStaff(actor);
        if (body == null || body.isBlank() || body.trim().length() > 5000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is invalid");
        if (leads.findAny(id).isEmpty()) throw notFound();
        return leads.addStaffMessage(id, actor.username(), body.trim(), internal, Instant.now());
    }

    @Transactional(readOnly = true)
    public Lead find(PartnerPrincipal principal, UUID id) {
        UUID ownerId = owner(principal);
        return leads.findOwned(id, ownerId).orElseThrow(() -> notFound());
    }

    @Transactional(readOnly = true)
    public List<LeadStatusEvent> statusHistory(PartnerPrincipal principal, UUID id) {
        Lead lead = find(principal,id);
        return leads.statusHistory(lead.id());
    }

    @Transactional(readOnly = true)
    public List<LeadMessage> messages(PartnerPrincipal principal, UUID id) {
        Lead lead = find(principal, id);
        return leads.publicMessages(lead.id()).stream().filter(message -> !message.internal()).toList();
    }

    @Transactional
    public LeadMessage sendMessage(PartnerPrincipal principal, UUID id, String body) {
        UUID ownerId = owner(principal);
        if (body == null || body.isBlank() || body.trim().length() > 5000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is invalid");
        if (leads.findOwned(id, ownerId).isEmpty()) throw notFound();
        return leads.addMessage(id, ownerId, body.trim(), Instant.now());
    }

    private UUID owner(PartnerPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner session required");
        return partners.profileId(principal);
    }
    private static void requireStaff(StaffPrincipal actor) {
        if (actor == null || actor.username() == null || actor.username().isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff session required");
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void validate(CreateLead value) {
        if (value == null || value.companyName() == null || value.companyName().isBlank() || value.companyName().trim().length() > 250
                || value.requestSummary() == null || value.requestSummary().isBlank() || value.requestSummary().trim().length() > 500
                || value.description() == null || value.description().isBlank() || value.description().trim().length() > 10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead details are invalid");
        if (value.budgetAmount() != null) {
            BigDecimal amount = value.budgetAmount();
            int fractionalDigits = Math.max(amount.scale(), 0);
            int integerDigits = Math.max(amount.precision() - amount.scale(), 0);
            if (amount.signum() < 0 || fractionalDigits > 2 || integerDigits > 14)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget is invalid");
        }
        if (value.budgetAmount() != null && (value.budgetCurrency() == null || !value.budgetCurrency().matches("[A-Z]{3}")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget currency is invalid");
        if (value.budgetCurrency() != null && !value.budgetCurrency().matches("[A-Z]{3}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget currency is invalid");
        if (value.decisionMakerEmail() != null && !value.decisionMakerEmail().isBlank()
                && !value.decisionMakerEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision maker email is invalid");
    }
    private static String encode(Lead lead) {
        String text = lead.updatedAt() + "/" + lead.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static Cursor decode(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8).split("/");
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor"); }
    }
    private record Cursor(Instant updatedAt, UUID id) { }
    public record CreateLead(String companyName, String domainOrTaxId, String requestSummary, BigDecimal budgetAmount,
            String budgetCurrency, LocalDate plannedStartAt, String decisionMakerName, String decisionMakerEmail, String description) { }
    public record Column(LeadLane lane, List<LeadStatus> statuses, LeadStatus status, long total, List<Lead> items, String nextCursor) { }
    public record Board(List<Column> columns) { }
    public record StaffDetail(Lead lead, List<LeadStatusEvent> history, List<LeadMessage> messages) { }
}
