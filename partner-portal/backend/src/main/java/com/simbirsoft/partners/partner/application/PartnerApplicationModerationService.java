package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleLookup;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationQueryRepository;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationRecord;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationStore;
import com.simbirsoft.partners.partner.infrastructure.RegistrationDataProtector;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class PartnerApplicationModerationService {
    private final PartnerApplicationStore store;
    private final PartnerApplicationQueryRepository queries;
    private final RegistrationDataProtector protector;
    private final StaffRoleLookup staffRoles;

    public PartnerApplicationModerationService(PartnerApplicationStore store,
            PartnerApplicationQueryRepository queries, RegistrationDataProtector protector,
            StaffRoleLookup staffRoles) {
        this.store = store;
        this.queries = queries;
        this.protector = protector;
        this.staffRoles = staffRoles;
    }

    @Transactional(readOnly = true)
    public ManualReviewPage manualReviewPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
        return new ManualReviewPage(queries.findManualReviewPage(page, size).stream()
                .map(row -> new ManualReviewItem(row.id(), row.submittedAt(), row.revisionNumber())).toList(),
                page, size, queries.countManualReview());
    }

    @Transactional(readOnly = true)
    public PartnerApplication detail(UUID id) {
        return getRecord(id).toDomain(protector);
    }

    @Transactional
    public PartnerApplication approve(UUID id, String actor, String assignedManagerUsername) {
        String manager = normalizeUsername(assignedManagerUsername);
        if (manager != null && !staffRoles.hasRole(manager, StaffRole.PARTNER_MANAGER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user is not a partner manager");
        }
        return decide(id, actor, PartnerApplicationStatus.APPROVED, null, manager);
    }

    @Transactional
    public PartnerApplication reject(UUID id, String actor, String reason) {
        return decide(id, actor, PartnerApplicationStatus.REJECTED, reason, null);
    }

    @Transactional
    public PartnerApplication requestChanges(UUID id, String actor, String reason) {
        return decide(id, actor, PartnerApplicationStatus.CHANGES_REQUESTED, reason, null);
    }

    private PartnerApplication decide(UUID id, String actor, PartnerApplicationStatus status,
                                      String reason, String manager) {
        var record = getRecord(id);
        var application = record.toDomain(protector);
        if (application.status() != PartnerApplicationStatus.MANUAL_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application was already decided");
        }
        PartnerApplication decided;
        try {
            decided = application.decide(status, actor, reason, manager, Instant.now());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid moderation decision");
        }
        record.updateFrom(decided, protector);
        try {
            store.saveAndFlush(record);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application was already decided");
        }
        return decided;
    }

    private PartnerApplicationRecord getRecord(UUID id) {
        return store.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String normalizeUsername(String username) {
        if (username == null) return null;
        String value = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.length() > 255 || value.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid manager username");
        }
        return value;
    }

    public record ManualReviewItem(UUID id, Instant submittedAt, int revisionNumber) { }
    public record ManualReviewPage(java.util.List<ManualReviewItem> items, int page, int size, long total) { }
}
