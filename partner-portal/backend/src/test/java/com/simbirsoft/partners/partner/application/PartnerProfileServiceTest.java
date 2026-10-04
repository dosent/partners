package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.partner.domain.LegalConsent;
import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerProfile;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.domain.PartnerType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerProfileServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Test
    void currentProfileIsLookedUpOnlyByAuthenticatedProviderAndSubject() {
        var store = new InMemoryProfileStore();
        PartnerApplication approved = approvedApplication("owner-1");
        PartnerProfile profile = PartnerProfile.approved("YANDEX", "owner-1", approved.id(), 1, NOW);
        store.save(profile);
        var source = new TrackingProfileSource();
        source.registration = Optional.of(registration("Свой партнёр"));
        var service = new PartnerProfileService(store, source);

        var current = service.current(new PartnerPrincipal("YANDEX", "owner-1"));

        assertThat(current.fullName()).isEqualTo("Свой партнёр");
        assertThat(store.lastLookup).isEqualTo("YANDEX:owner-1");
        assertThat(current).extracting("id").isEqualTo(profile.id());
    }

    @Test
    void missingOwnerProfileReturnsNotFoundWithoutReadingAnotherPartnerData() {
        var store = new InMemoryProfileStore();
        PartnerApplication approved = approvedApplication("owner-1");
        store.save(PartnerProfile.approved("YANDEX", "owner-1", approved.id(), 1, NOW));
        var source = new TrackingProfileSource();
        var service = new PartnerProfileService(store, source);

        assertThatThrownBy(() -> service.current(new PartnerPrincipal("YANDEX", "owner-2")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404);
        assertThat(source.wasRead).isFalse();
    }

    @Test
    void approvalProvisioningIsIdempotentForTheSameOwnerAndApplication() {
        var store = new InMemoryProfileStore();
        var service = new PartnerProfileService(store, new TrackingProfileSource());
        PartnerApplication approved = approvedApplication("owner-1");

        service.activate(approved);
        UUID firstId = store.byIdentity.get("YANDEX:owner-1").id();
        service.activate(approved);

        assertThat(store.byIdentity).hasSize(1);
        assertThat(store.byIdentity.get("YANDEX:owner-1").id()).isEqualTo(firstId);
    }

    @Test
    void approvedApplicationWithoutProfileIsLazilyMaterializedWithItsApprovedRevision() {
        var store = new InMemoryProfileStore();
        PartnerApplication approved = approvedApplication("legacy-owner");
        PartnerProfile profile = PartnerProfile.approved("YANDEX", "legacy-owner", approved.id(), 1, NOW.plusSeconds(30));
        var data = registration("Одобренный партнёр");
        ApprovedPartnerProfileSource source = new ApprovedPartnerProfileSource() {
            @Override
            public Optional<ApprovedProfile> findApprovedProfile(String provider, String subject) {
                return provider.equals("YANDEX") && subject.equals("legacy-owner")
                        ? Optional.of(new ApprovedProfile(profile, data)) : Optional.empty();
            }

            @Override
            public Optional<PartnerRegistrationData> findApprovedRegistration(PartnerProfile ignored) {
                return Optional.of(data);
            }
        };

        var current = new PartnerProfileService(store, source)
                .current(new PartnerPrincipal("YANDEX", "legacy-owner"));

        assertThat(current.fullName()).isEqualTo("Одобренный партнёр");
        assertThat(store.byIdentity.get("YANDEX:legacy-owner").id()).isEqualTo(profile.id());
        assertThat(store.byIdentity.get("YANDEX:legacy-owner").approvedRevisionNumber()).isEqualTo(1);
    }

    private static PartnerApplication approvedApplication(String subject) {
        return PartnerApplication.submit(UUID.randomUUID(), "YANDEX", subject, registration("Test Partner"),
                        new LegalConsent("RU", "offer-1", NOW, "privacy-1", NOW), NOW)
                .decide(PartnerApplicationStatus.APPROVED, "ivan.neshin", null, null, NOW.plusSeconds(30));
    }

    private static PartnerRegistrationData registration(String name) {
        return new PartnerRegistrationData(PartnerType.INDIVIDUAL, name, "partner@example.test", "+70000000000",
                "RU", "Ульяновск", null, null);
    }

    private static class InMemoryProfileStore implements PartnerProfileStore {
        private final Map<String, PartnerProfile> byIdentity = new HashMap<>();
        private String lastLookup;

        @Override
        public Optional<PartnerProfile> findByIdentity(String provider, String subject) {
            lastLookup = provider + ":" + subject;
            return Optional.ofNullable(byIdentity.get(lastLookup));
        }

        @Override
        public PartnerProfile save(PartnerProfile profile) {
            byIdentity.put(profile.identityProvider() + ":" + profile.externalSubject(), profile);
            return profile;
        }
    }

    private static class TrackingProfileSource implements ApprovedPartnerProfileSource {
        private boolean wasRead;
        private Optional<PartnerRegistrationData> registration = Optional.empty();

        @Override
        public Optional<ApprovedProfile> findApprovedProfile(String provider, String subject) {
            return Optional.empty();
        }

        @Override
        public Optional<PartnerRegistrationData> findApprovedRegistration(PartnerProfile profile) {
            wasRead = true;
            return registration;
        }
    }
}
