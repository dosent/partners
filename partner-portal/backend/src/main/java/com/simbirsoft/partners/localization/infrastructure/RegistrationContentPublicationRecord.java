package com.simbirsoft.partners.localization.infrastructure;

import com.simbirsoft.partners.localization.application.RegistrationContentStore.PublishedState;
import com.simbirsoft.partners.localization.domain.RegistrationContentDraft;
import com.simbirsoft.partners.localization.domain.RegistrationLocale;
import com.simbirsoft.partners.localization.domain.OfferDocument;
import com.simbirsoft.partners.localization.domain.OfferType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Опубликованный snapshot; поля помечены неизменяемыми и publication API его не редактирует. */
@Entity
@Table(name = "registration_content_publication", uniqueConstraints = @UniqueConstraint(
        name = "uq_registration_content_publication_version", columnNames = {"locale", "content_version"}))
class RegistrationContentPublicationRecord {
    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(nullable = false, length = 2, updatable = false)
    private String locale;

    @Column(name = "content_version", nullable = false, updatable = false)
    private int contentVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strings_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> strings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offers_json", columnDefinition = "jsonb", updatable = false)
    private Map<OfferType, OfferDocument> offers;
    @Column(name = "privacy_notice_version", nullable = false, length = 100, updatable = false) private String privacyNoticeVersion;
    @Column(name = "privacy_notice_title", nullable = false, columnDefinition = "text", updatable = false) private String privacyNoticeTitle;
    @Column(name = "privacy_notice_body", nullable = false, columnDefinition = "text", updatable = false) private String privacyNoticeBody;
    @Column(name = "logo_asset_key", nullable = false, length = 32, updatable = false) private String logoAssetKey;
    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false) private String checksum;
    @Column(name = "published_by", nullable = false, length = 255, updatable = false) private String publishedBy;
    @Column(name = "published_at", nullable = false, updatable = false) private Instant publishedAt;

    protected RegistrationContentPublicationRecord() { }

    RegistrationContentPublicationRecord(RegistrationLocale locale, int version, RegistrationContentDraft content,
            String checksum, String actor, Instant publishedAt) {
        this.id = UUID.randomUUID();
        this.locale = locale.value();
        this.contentVersion = version;
        this.strings = content.strings();
        this.offers = content.offers();
        this.privacyNoticeVersion = content.privacyNoticeVersion();
        this.privacyNoticeTitle = content.privacyNoticeTitle();
        this.privacyNoticeBody = content.privacyNoticeBody();
        this.logoAssetKey = content.logoAssetKey();
        this.checksum = checksum;
        this.publishedBy = actor;
        this.publishedAt = publishedAt;
    }

    UUID id() { return id; }
    PublishedState toState() {
        return new PublishedState(id, RegistrationLocale.valueOf(locale), contentVersion,
                new RegistrationContentDraft(strings, offers,
                        privacyNoticeVersion, privacyNoticeTitle, privacyNoticeBody, logoAssetKey),
                checksum, publishedBy, publishedAt);
    }
}
