package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.audit.application.PartnerProfileChangeAudit;
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
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PartnerProfileService {
    private final PartnerProfileStore profiles;
    private final ApprovedPartnerProfileSource approvedSource;
    private final PartnerProfileContactsProtector contactsProtector;
    private final PartnerProfileChangeAudit changeAudit;

    public PartnerProfileService(PartnerProfileStore profiles, ApprovedPartnerProfileSource approvedSource,
            PartnerProfileContactsProtector contactsProtector, PartnerProfileChangeAudit changeAudit) {
        this.profiles = profiles;
        this.approvedSource = approvedSource;
        this.contactsProtector = contactsProtector;
        this.changeAudit = changeAudit;
    }

    @Transactional
    public PartnerProfile activate(PartnerApplication application) {
        if (application.status() != PartnerApplicationStatus.APPROVED || application.revisions().isEmpty()) {
            throw new IllegalArgumentException("Only an approved application can activate a profile");
        }
        Instant approvedAt = application.statusHistory().getLast().changedAt();
        var approvedRevision = application.revisions().getLast();
        int revision = approvedRevision.revisionNumber();
        String locale = approvedRevision.consent().locale();
        PartnerProfile profile = profiles.findByIdentity(application.identityProvider(), application.externalSubject())
                .map(existing -> existing.refresh(application.identityProvider(), application.externalSubject(),
                        application.id(), revision, approvedAt))
                .orElseGet(() -> PartnerProfile.approved(application.identityProvider(), application.externalSubject(),
                        application.id(), revision, approvedAt, locale));
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
        return details(profile, data);
    }

    @Transactional
    public ProfileDetails updateCurrent(PartnerPrincipal owner, ProfileUpdate update) {
        if (owner == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner session is required");
        PartnerProfile profile = profiles.findByIdentity(owner.provider(), owner.subject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner profile not found"));
        PartnerRegistrationData registration = approvedSource.findApprovedRegistration(profile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner profile not found"));
        validate(update);
        PartnerProfileContactsProtector.ProfileContacts nextContacts = new PartnerProfileContactsProtector.ProfileContacts(update.fullName().trim(), update.email().trim(),
                update.phone().trim());
        PartnerProfileContactsProtector.ProfileContacts currentContacts = currentContacts(profile, registration);
        var changedFields = new ArrayList<String>();
        if (!currentContacts.fullName().equals(nextContacts.fullName())) changedFields.add("fullName");
        if (!currentContacts.email().equalsIgnoreCase(nextContacts.email())) changedFields.add("email");
        if (!currentContacts.phone().equals(nextContacts.phone())) changedFields.add("phone");
        if (!profile.locale().equals(update.locale())) changedFields.add("locale");
        if (profile.emailNotificationsEnabled() != update.emailNotificationsEnabled()) changedFields.add("emailNotificationsEnabled");
        if (profile.inAppNotificationsEnabled() != update.inAppNotificationsEnabled()) changedFields.add("inAppNotificationsEnabled");
        if (changedFields.isEmpty()) return details(profile, registration);

        Instant changedAt = Instant.now();
        PartnerProfile saved = profiles.save(profile.withSettings(contactsProtector.protect(nextContacts),
                update.locale(), update.emailNotificationsEnabled(), update.inAppNotificationsEnabled(), changedAt));
        changeAudit.record(saved.id(), owner.provider(), owner.subject(), changedFields, changedAt);
        return details(saved, registration);
    }

    private ProfileDetails details(PartnerProfile profile, PartnerRegistrationData registration) {
        PartnerProfileContactsProtector.ProfileContacts contacts = currentContacts(profile, registration);
        return new ProfileDetails(profile.id(), registration.partnerType(), contacts.fullName(), contacts.email(),
                contacts.phone(), registration.countryCode(), registration.region(), registration.companyName(),
                profile.approvedAt(), profile.locale(), profile.emailNotificationsEnabled(),
                profile.inAppNotificationsEnabled());
    }

    private PartnerProfileContactsProtector.ProfileContacts currentContacts(PartnerProfile profile, PartnerRegistrationData registration) {
        if (profile.encryptedContactOverrides() != null) return contactsProtector.reveal(profile.encryptedContactOverrides());
        return new PartnerProfileContactsProtector.ProfileContacts(registration.fullName(), registration.email(), registration.phone());
    }

    private static void validate(ProfileUpdate update) {
        if (update == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile settings are required");
        String name = update.fullName() == null ? "" : update.fullName().trim();
        String email = update.email() == null ? "" : update.email().trim();
        String phone = update.phone() == null ? "" : update.phone().trim();
        if (name.isBlank() || name.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fullName is invalid");
        if (!EMAIL.matcher(email).matches() || email.length() > 254) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is invalid");
        if (!PHONE.matcher(phone).matches() || phone.replaceAll("\\D", "").length() < 7
                || phone.replaceAll("\\D", "").length() > 15) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phone is invalid");
        if (update.locale() == null || !Set.of("RU", "KZ", "EN").contains(update.locale())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "locale is invalid");
        }
    }

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+]?[-0-9(). ]+$");

    public record ProfileUpdate(String fullName, String email, String phone, String locale,
            boolean emailNotificationsEnabled, boolean inAppNotificationsEnabled) { }

    public record ProfileDetails(UUID id, com.simbirsoft.partners.partner.domain.PartnerType partnerType,
            String fullName, String email, String phone, String countryCode, String region,
            String companyName, Instant approvedAt, String locale, boolean emailNotificationsEnabled,
            boolean inAppNotificationsEnabled) { }
}
