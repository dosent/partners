package com.simbirsoft.partners.localization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.localization.application.RegistrationContentService.ContentIncompleteException;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.DraftState;
import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.RegistrationLocale;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegistrationContentServiceProfileKeysTest {
    @Test
    void publicationRequiresProfileLabelsAndStatusesForEveryLocale() {
        RegistrationContentStore store = mock(RegistrationContentStore.class);
        RegistrationContentDraft incomplete = new RegistrationContentDraft(
                Map.of(), Map.of(), "", "", "", "");
        for (RegistrationLocale locale : RegistrationLocale.values()) {
            when(store.findDraft(locale))
                    .thenReturn(Optional.of(new DraftState(incomplete, 1, "admin", Instant.EPOCH)));
        }
        RegistrationContentService service = new RegistrationContentService(
                store, new ObjectMapper(), "simbirsoft,17soft");

        for (RegistrationLocale locale : RegistrationLocale.values()) {
            ContentIncompleteException error = null;
            try {
                service.publish(locale.value(), 1, "admin");
            } catch (ContentIncompleteException exception) {
                error = exception;
            }
            assertThat(error).isNotNull();
            assertThat(error.missingFields()).contains(
                    "portal.profile.navigation",
                    "portal.profile.title",
                    "portal.profile.description",
                    "portal.profile.settings.title",
                    "portal.profile.locale.label",
                    "portal.profile.locale.RU",
                    "portal.profile.locale.KZ",
                    "portal.profile.locale.EN",
                    "portal.profile.notifications.title",
                    "portal.profile.notifications.email",
                    "portal.profile.notifications.inApp",
                    "portal.profile.actions.save",
                    "portal.profile.actions.saving",
                    "portal.profile.status.saved",
                    "portal.profile.errors.save",
                    "portal.profile.loading",
                    "portal.profile.errors.load",
                    "portal.profile.errors.localeUnavailable");
        }
    }
}
