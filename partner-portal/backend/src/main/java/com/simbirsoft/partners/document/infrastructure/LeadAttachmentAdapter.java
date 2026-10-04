package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.LeadDocumentService;
import com.simbirsoft.partners.lead.application.LeadAttachmentPort;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adapts document use cases to the lead module's application-level contract. */
@Component
@Profile("!auth-test")
class LeadAttachmentAdapter implements LeadAttachmentPort {
    private final LeadDocumentService documents;

    LeadAttachmentAdapter(LeadDocumentService documents) {
        this.documents = documents;
    }

    @Override
    public PreparedAttachment prepare(String fileName, String contentType, byte[] content) {
        var prepared = documents.prepare(fileName, contentType, content);
        return new PreparedAttachment(prepared.fileName(), prepared.contentType(), prepared.bytes(), prepared.sha256());
    }

    @Override
    public Attachment persist(UUID ownerId, UUID leadId, PreparedAttachment attachment) {
        var prepared = new LeadDocumentService.PreparedAttachment(
                attachment.fileName(), attachment.contentType(), attachment.content(), attachment.sha256());
        return view(documents.persist(ownerId, leadId, prepared));
    }

    @Override
    public List<Attachment> forLead(UUID leadId) {
        return documents.forLead(leadId).stream().map(LeadAttachmentAdapter::view).toList();
    }

    @Override
    public Download download(UUID leadId, UUID attachmentId, UUID ownerId) {
        var download = documents.download(leadId, attachmentId, ownerId);
        return new Download(download.fileName(), download.contentType(), download.bytes());
    }

    private static Attachment view(com.simbirsoft.partners.document.domain.LeadAttachment attachment) {
        return new Attachment(attachment.id(), attachment.leadId(), attachment.fileName(), attachment.status().name());
    }
}
