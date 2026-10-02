package com.simbirsoft.partners.localization.infrastructure;

import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.RegistrationLocale;
import com.simbirsoft.partners.localization.domain.OfferDocument;
import com.simbirsoft.partners.localization.domain.OfferType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registration_content_draft")
class RegistrationContentDraftRecord {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 2, updatable = false)
    private String locale;

    @Version
    @Column(name = "draft_version", nullable = false)
    private Long draftVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strings_json", columnDefinition = "jsonb")
    private Map<String, String> strings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offers_json", columnDefinition = "jsonb")
    private Map<OfferType, OfferDocument> offers;
    @Column(name = "privacy_notice_version", length = 100) private String privacyNoticeVersion;
    @Column(name = "privacy_notice_title", columnDefinition = "text") private String privacyNoticeTitle;
    @Column(name = "privacy_notice_body", columnDefinition = "text") private String privacyNoticeBody;
    @Column(name = "logo_asset_key", length = 32) private String logoAssetKey;
    @Column(name = "updated_by", nullable = false, length = 255) private String updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected RegistrationContentDraftRecord() { }

    RegistrationContentDraftRecord(RegistrationLocale locale, RegistrationContentDraft content,
            String actor, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.locale = locale.value();
        this.updatedBy = actor;
        this.updatedAt = changedAt;
        replaceContent(content);
    }

    void replaceContent(RegistrationContentDraft content) {
        this.strings = content.strings();
        this.offers = content.offers();
        this.privacyNoticeVersion = content.privacyNoticeVersion();
        this.privacyNoticeTitle = content.privacyNoticeTitle();
        this.privacyNoticeBody = content.privacyNoticeBody();
        this.logoAssetKey = content.logoAssetKey();
    }

    void updateAudit(String actor, Instant changedAt) {
        this.updatedBy = actor;
        this.updatedAt = changedAt;
    }

    RegistrationLocale locale() { return RegistrationLocale.valueOf(locale); }
    long draftVersion() { return draftVersion == null ? 0L : draftVersion; }
    String updatedBy() { return updatedBy; }
    Instant updatedAt() { return updatedAt; }
    RegistrationContentDraft content() {
        return new RegistrationContentDraft(strings, offers,
                privacyNoticeVersion, privacyNoticeTitle, privacyNoticeBody, logoAssetKey);
    }
}
