package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PartnerApplicationOwnerStatusService {
    private final PartnerApplicationStore store;

    public PartnerApplicationOwnerStatusService(PartnerApplicationStore store) { this.store = store; }

    @Transactional(readOnly = true)
    public OwnerStatus status(String provider, String subject) {
        var application = store.findByIdentityProviderAndExternalSubject(provider, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var lastEvent = application.getStatusHistory().isEmpty() ? null
                : application.getStatusHistory().get(application.getStatusHistory().size() - 1).toDomain();
        return new OwnerStatus(application.getStatus(), lastEvent == null ? null : lastEvent.reason());
    }

    public record OwnerStatus(PartnerApplicationStatus status, String latestDecisionReason) { }
}
