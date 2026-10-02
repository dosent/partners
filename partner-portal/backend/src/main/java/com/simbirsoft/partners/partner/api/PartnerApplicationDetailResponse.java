package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.domain.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Карточка заявки для уполномоченного модератора")
public record PartnerApplicationDetailResponse(
        UUID id,
        PartnerApplicationStatus status,
        String assignedManagerUsername,
        Revision currentRevision,
        List<StatusEvent> statusHistory) {
    public static PartnerApplicationDetailResponse from(PartnerApplication application) {
        var revision = application.revisions().getLast();
        var data = revision.registrationData();
        var consent = revision.consent();
        return new PartnerApplicationDetailResponse(application.id(), application.status(),
                application.assignedManagerUsername(),
                new Revision(revision.revisionNumber(), revision.submittedAt(),
                        new RegistrationData(data.partnerType(), data.fullName(), data.email(), data.phone(),
                                data.countryCode(), data.region(), data.companyName(), data.taxIdentifier()),
                        new Consent(consent.locale(), consent.offerVersion(), consent.offerAcceptedAt(),
                                consent.privacyNoticeVersion(), consent.privacyAcceptedAt())),
                application.statusHistory().stream().map(event -> new StatusEvent(event.status(),
                        event.changedBy(), event.reason(), event.changedAt())).toList());
    }

    @Schema(description = "Редакция заявки")
    public record Revision(int revisionNumber, Instant submittedAt, RegistrationData data, Consent consent) { }
    @Schema(description = "Данные партнёра для рассмотрения")
    public record RegistrationData(PartnerType partnerType, String fullName, String email, String phone,
                                   String countryCode, String region, String companyName, String taxIdentifier) { }
    @Schema(description = "Принятые версии документов")
    public record Consent(String locale, String offerVersion, Instant offerAcceptedAt,
                          String privacyNoticeVersion, Instant privacyAcceptedAt) { }
    @Schema(description = "Запись неизменяемой истории статусов")
    public record StatusEvent(PartnerApplicationStatus status, String changedBy, String reason, Instant changedAt) { }
}
