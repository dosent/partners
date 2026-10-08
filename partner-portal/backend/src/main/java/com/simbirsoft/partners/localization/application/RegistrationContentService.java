package com.simbirsoft.partners.localization.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.DraftState;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.PublishedState;
import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.RegistrationLocale;
import com.simbirsoft.partners.localization.domain.OfferDocument;
import com.simbirsoft.partners.localization.domain.OfferType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Управляет черновиками и атомарно публикует полные snapshots регистрации. */
@Service
public class RegistrationContentService implements PublishedRegistrationContentLookup {
    private static final List<String> REQUIRED_STRING_KEYS = List.of(
            "registration.title",
            "registration.types.individual",
            "registration.types.legalEntity",
            "registration.types.soleProprietor",
            "registration.fields.fullName",
            "registration.fields.email",
            "registration.fields.phone",
            "registration.fields.country",
            "registration.fields.region",
            "registration.fields.companyName",
            "registration.fields.taxIdentifier",
            "registration.consent.offer",
            "registration.consent.privacy",
            "registration.actions.submit",
            "registration.countries.RU",
            "registration.countries.KZ",
            "registration.status.manualReview",
            "registration.ui.loading",
            "registration.auth.title",
            "registration.auth.info",
            "registration.auth.action",
            "registration.auth.back",
            "registration.errors.conflict",
            "registration.errors.unavailable",
            "registration.errors.sessionExpired",
            "registration.errors.generic",
            "registration.errors.taxIdentifier",
            "registration.status.PENDING_REVIEW",
            "registration.status.MANUAL_REVIEW",
            "registration.status.CHANGES_REQUESTED",
            "registration.status.REJECTED",
            "registration.status.APPROVED",
            "registration.status.approvedInfo",
            "registration.status.pendingInfo",
            "registration.status.resubmitInfo",
            "registration.actions.resubmit",
            "registration.actions.retry",
            "registration.actions.refresh",
            "registration.actions.submitProgress",
            "registration.documents.published",
            "registration.documents.revision",
            "portal.title",
            "portal.status.approved",
            "portal.section.overview",
            "portal.section.leads",
            "portal.section.vacancies",
            "portal.section.finance",
            "portal.section.documents",
            "portal.welcome",
            "portal.emptySection",
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
            "portal.profile.errors.localeUnavailable",
            "portal.apiKeys.navigation",
            "portal.apiKeys.title",
            "portal.apiKeys.description",
            "portal.apiKeys.empty",
            "portal.apiKeys.name.label",
            "portal.apiKeys.actions.create",
            "portal.apiKeys.actions.creating",
            "portal.apiKeys.actions.revoke",
            "portal.apiKeys.actions.cancel",
            "portal.apiKeys.status.created",
            "portal.apiKeys.status.revoked",
            "portal.apiKeys.loading",
            "portal.apiKeys.errors.load",
            "portal.apiKeys.errors.create",
            "portal.apiKeys.errors.revoke",
            "portal.apiKeys.secret.title",
            "portal.apiKeys.secret.description",
            "portal.apiKeys.revoke.confirm");
    private static final List<String> REQUIRED_STAFFING_KEYS = List.of(
            "staffing.list.eyebrow",
            "staffing.list.title",
            "staffing.list.description",
            "staffing.list.create",
            "staffing.list.loading",
            "staffing.list.emptyTitle",
            "staffing.list.emptyDescription",
            "staffing.list.refresh",
            "staffing.status.NEW",
            "staffing.form.back",
            "staffing.form.eyebrow",
            "staffing.form.title",
            "staffing.form.description",
            "staffing.form.context",
            "staffing.form.project",
            "staffing.form.projectPlaceholder",
            "staffing.form.projectHelp",
            "staffing.form.role",
            "staffing.form.position",
            "staffing.form.headcount",
            "staffing.form.grade",
            "staffing.form.chooseGrade",
            "staffing.form.requiredSkills",
            "staffing.form.skillsHelp",
            "staffing.form.preferredSkills",
            "staffing.form.tasks",
            "staffing.form.work",
            "staffing.form.hours",
            "staffing.form.workMode",
            "staffing.form.remote",
            "staffing.form.hybrid",
            "staffing.form.office",
            "staffing.form.location",
            "staffing.form.outsideRussia",
            "staffing.form.flexibleDate",
            "staffing.form.desiredDate",
            "staffing.form.selection",
            "staffing.form.language",
            "staffing.form.noLanguage",
            "staffing.form.languageLevel",
            "staffing.form.chooseLanguageLevel",
            "staffing.form.interview",
            "staffing.form.screening",
            "staffing.form.cv",
            "staffing.form.files",
            "staffing.form.optional",
            "staffing.form.brief",
            "staffing.form.cvExample",
            "staffing.form.fileHelp",
            "staffing.form.cancel",
            "staffing.form.submit",
            "staffing.form.saving",
            "staffing.form.invalidFile",
            "staffing.success.eyebrow",
            "staffing.success.status",
            "staffing.success.list",
            "staffing.success.new",
            "staffing.success.uploadFailed",
            "staffing.success.retryUpload",
            "staffing.errors.retry",
            "staffing.list.flexibleDate",
            "staffing.form.languageEnglish",
            "staffing.form.languageRussian",
            "staffing.form.languageKazakh",
            "staffing.errors.validation",
            "staffing.errors.unavailable",
            "staffing.errors.submit",
            "staffing.errors.upload",
            "staffing.list.more",
            "staffing.form.positionPlaceholder",
            "staffing.form.nativeLanguage",
            "staffing.errors.field",
            "staffing.list.submitted",
            "staffing.list.files",
            "staffing.form.continueWithoutFile");
    private static final List<String> LOGO_ASSET_KEYS = List.of("simbirsoft", "17soft");

    private final RegistrationContentStore store;
    private final ObjectMapper objectMapper;
    private final List<String> approvedLogoAssetKeys;

    public RegistrationContentService(RegistrationContentStore store, ObjectMapper objectMapper,
            @Value("${partners.registration.approved-logo-asset-keys:}") String approvedLogoAssetKeys) {
        this.store = store;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.approvedLogoAssetKeys = approvedLogoAssetKeys == null ? List.of()
                : java.util.Arrays.stream(approvedLogoAssetKeys.split(","))
                    .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedRegistrationContent> findPublished(String locale) {
        RegistrationLocale requested = RegistrationLocale.publicOrDefault(locale);
        return store.findCurrentPublication(requested)
                .map(this::toLookup);
    }

    @Transactional(readOnly = true)
    public AdminState getAdminState(String localeValue) {
        RegistrationLocale locale = requireAdminLocale(localeValue);
        return new AdminState(locale.value(), store.findDraft(locale).orElse(null),
                store.findCurrentPublication(locale).orElse(null));
    }

    @Transactional
    public AdminState saveDraft(String localeValue, DraftCommand command, String actor) {
        RegistrationLocale locale = requireAdminLocale(localeValue);
        validateDraftLogoKey(command.content().logoAssetKey());
        try {
            store.saveDraft(locale, command.content(), command.expectedDraftVersion(), actor, Instant.now());
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw conflict();
        }
        return getAdminState(locale.value());
    }

    @Transactional
    public PublishedRegistrationContent publish(String localeValue, long expectedDraftVersion, String actor) {
        RegistrationLocale locale = requireAdminLocale(localeValue);
        DraftState draft = store.findDraft(locale)
                .orElseThrow(() -> new ContentIncompleteException(List.of("draft")));
        if (draft.draftVersion() != expectedDraftVersion) throw conflict();

        List<String> missing = missingContent(locale, draft.content());
        if (!missing.isEmpty()) throw new ContentIncompleteException(missing);

        int version = store.nextPublicationVersion(locale);
        String checksum = checksum(locale, version, draft.content());
        Instant publishedAt = Instant.now();
        try {
            DraftState claimedDraft = store.claimDraftForPublication(locale, expectedDraftVersion, actor, publishedAt);
            PublishedState published = store.appendPublication(locale, version, claimedDraft.content(), checksum,
                    actor, publishedAt);
            store.setCurrentPublication(locale, published.id());
            return toLookup(published);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    public static RegistrationLocale requireAdminLocale(String value) {
        return RegistrationLocale.parse(value).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported registration locale"));
    }

    private void validateDraftLogoKey(String key) {
        if (key != null && !key.isBlank() && !LOGO_ASSET_KEYS.contains(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported logo asset key");
        }
    }

    private List<String> missingContent(RegistrationLocale locale, RegistrationContentDraft content) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_STRING_KEYS) {
            if (content.strings().get(key) == null || content.strings().get(key).isBlank()) missing.add(key);
        }
        for (String key : REQUIRED_STAFFING_KEYS) {
            if (content.strings().get(key) == null || content.strings().get(key).isBlank()) missing.add(key);
        }
        for (OfferType type : OfferType.values()) {
            OfferDocument offer = content.offers().get(type);
            requireText(offer == null ? null : offer.version(), "offers." + type + ".version", missing);
            requireText(offer == null ? null : offer.title(), "offers." + type + ".title", missing);
            requireText(offer == null ? null : offer.body(), "offers." + type + ".body", missing);
        }
        requireText(content.privacyNoticeVersion(), "privacyNoticeVersion", missing);
        requireText(content.privacyNoticeTitle(), "privacyNoticeTitle", missing);
        requireText(content.privacyNoticeBody(), "privacyNoticeBody", missing);
        if (!locale.requiredLogoAssetKey().equals(content.logoAssetKey())) {
            missing.add("logoAssetKey (expected " + locale.requiredLogoAssetKey() + ")");
        } else if (!approvedLogoAssetKeys.contains(content.logoAssetKey())) {
            missing.add("approvedLogoAssetKey (not configured)");
        }
        return List.copyOf(missing);
    }

    private static void requireText(String value, String field, List<String> missing) {
        if (value == null || value.isBlank()) missing.add(field);
    }

    private String checksum(RegistrationLocale locale, int version, RegistrationContentDraft content) {
        CanonicalContent canonical = new CanonicalContent(locale.value(), version,
                new TreeMap<>(content.strings()), new TreeMap<>(content.offers()),
                content.privacyNoticeVersion(), content.privacyNoticeTitle(), content.privacyNoticeBody(),
                content.logoAssetKey());
        try {
            return sha256(objectMapper.writeValueAsString(canonical));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to calculate registration content checksum", exception);
        }
    }

    private String documentChecksum(String version, String title, String body) {
        try {
            return sha256(objectMapper.writeValueAsString(new OfferDocument(version, title, body)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to calculate document checksum", exception);
        }
    }

    private static String sha256(String canonicalText) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private PublishedRegistrationContent toLookup(PublishedState published) {
        RegistrationContentDraft content = published.content();
        Map<OfferType, PublishedDocument> offers = new EnumMap<>(OfferType.class);
        content.offers().forEach((type, offer) -> offers.put(type,
                new PublishedDocument(offer.version(), offer.title(), offer.body(),
                        documentChecksum(offer.version(), offer.title(), offer.body()))));
        return new PublishedRegistrationContent(published.locale().value(), published.contentVersion(),
                content.strings(), offers,
                new PublishedDocument(content.privacyNoticeVersion(), content.privacyNoticeTitle(),
                        content.privacyNoticeBody(), documentChecksum(content.privacyNoticeVersion(),
                        content.privacyNoticeTitle(), content.privacyNoticeBody())),
                content.logoAssetKey(), published.checksum(), published.publishedAt());
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Registration content draft changed concurrently");
    }

    public record DraftCommand(long expectedDraftVersion, RegistrationContentDraft content) { }
    public record AdminState(String locale, DraftState draft, PublishedState published) { }
    private record CanonicalContent(String locale, int contentVersion, Map<String, String> strings,
            Map<OfferType, OfferDocument> offers,
            String privacyNoticeVersion, String privacyNoticeTitle, String privacyNoticeBody,
            String logoAssetKey) { }

    public static final class ContentIncompleteException extends RuntimeException {
        private final List<String> missingFields;
        public ContentIncompleteException(List<String> missingFields) {
            super("Registration content is incomplete");
            this.missingFields = List.copyOf(missingFields);
        }
        public List<String> missingFields() { return missingFields; }
    }
}
