package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.partner.application.ApprovedPartnerProfileSource;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerProfile;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
class JpaApprovedPartnerProfileSource implements ApprovedPartnerProfileSource {
    private final PartnerApplicationStore applications;
    private final RegistrationDataProtector protector;

    JpaApprovedPartnerProfileSource(PartnerApplicationStore applications, RegistrationDataProtector protector) {
        this.applications = applications;
        this.protector = protector;
    }

    @Override
    public Optional<ApprovedProfile> findApprovedProfile(String identityProvider, String externalSubject) {
        return applications.findByIdentityProviderAndExternalSubject(identityProvider, externalSubject)
                .map(application -> application.toDomain(protector))
                .flatMap(this::toLatestApprovedProfile);
    }

    @Override
    public Optional<PartnerRegistrationData> findApprovedRegistration(PartnerProfile profile) {
        return applications.findById(profile.applicationId()).filter(application ->
                        application.getIdentityProvider().equals(profile.identityProvider())
                                && application.getExternalSubject().equals(profile.externalSubject()))
                .flatMap(application -> {
                    var domain = application.toDomain(protector);
                    boolean matchingApproval = domain.statusHistory().stream().anyMatch(event ->
                            event.status() == PartnerApplicationStatus.APPROVED
                                    && event.changedAt().equals(profile.approvedAt()));
                    if (!matchingApproval) return Optional.empty();
                    return domain.revisions().stream()
                            .filter(revision -> revision.revisionNumber() == profile.approvedRevisionNumber()
                                    && !revision.submittedAt().isAfter(profile.approvedAt()))
                            .map(com.simbirsoft.partners.partner.domain.PartnerApplicationRevision::registrationData)
                            .findFirst();
                });
    }

    private Optional<ApprovedProfile> toLatestApprovedProfile(PartnerApplication application) {
        var approval = application.statusHistory().stream()
                .filter(event -> event.status() == PartnerApplicationStatus.APPROVED)
                .max(java.util.Comparator.comparing(
                        com.simbirsoft.partners.partner.domain.PartnerApplicationStatusEvent::changedAt));
        if (approval.isEmpty()) return Optional.empty();
        var approvedAt = approval.get().changedAt();
        var revision = application.revisions().stream()
                .filter(candidate -> !candidate.submittedAt().isAfter(approvedAt))
                .max(java.util.Comparator.comparingInt(
                        com.simbirsoft.partners.partner.domain.PartnerApplicationRevision::revisionNumber));
        if (revision.isEmpty()) return Optional.empty();
        var profile = PartnerProfile.approved(application.identityProvider(), application.externalSubject(),
                application.id(), revision.get().revisionNumber(), approvedAt);
        return Optional.of(new ApprovedProfile(profile, revision.get().registrationData()));
    }
}
