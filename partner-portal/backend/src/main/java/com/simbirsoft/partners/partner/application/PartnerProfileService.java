package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerProfile;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class PartnerProfileService {
    private final PartnerProfileStore profiles;
    private final ApprovedPartnerProfileSource approvedSource;

    public PartnerProfileService(PartnerProfileStore profiles, ApprovedPartnerProfileSource approvedSource) {
        this.profiles = profiles;
        this.approvedSource = approvedSource;
    }

    @Transactional
    public PartnerProfile activate(PartnerApplication application) {
        if (application.status() != PartnerApplicationStatus.APPROVED || application.revisions().isEmpty()) {
            throw new IllegalArgumentException("Only an approved application can activate a profile");
        }
        Instant approvedAt = application.statusHistory().getLast().changedAt();
        int revision = application.revisions().getLast().revisionNumber();
        PartnerProfile profile = profiles.findByIdentity(application.identityProvider(), application.externalSubject())
                .map(existing -> existing.refresh(application.identityProvider(), application.externalSubject(),
                        application.id(), revision, approvedAt))
                .orElseGet(() -> PartnerProfile.approved(application.identityProvider(), application.externalSubject(),
                        application.id(), revision, approvedAt));
        return profiles.save(profile);
    }

    @Transactional
    public ProfileDetails current(PartnerPrincipal owner) {
        if (owner == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner session is required");
        PartnerProfile profile;
        PartnerRegistrationData data;
        var existing = profiles.findByIdentity(owner.provider(), owner.subject());
        if (existing.isPresent()) {
            profile = existing.get();
            data = approvedSource.findApprovedRegistration(profile)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner profile not found"));
        } else {
            var legacyApproval = approvedSource.findApprovedProfile(owner.provider(), owner.subject())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner profile not found"));
            profile = profiles.save(legacyApproval.profile());
            data = legacyApproval.registrationData();
        }
        return new ProfileDetails(profile.id(), data.partnerType(), data.fullName(), data.email(), data.phone(),
                data.countryCode(), data.region(), data.companyName(), profile.approvedAt());
    }

    public record ProfileDetails(UUID id, com.simbirsoft.partners.partner.domain.PartnerType partnerType,
            String fullName, String email, String phone, String countryCode, String region,
            String companyName, Instant approvedAt) { }
}
