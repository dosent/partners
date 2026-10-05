package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.LeadDocumentService;
import com.simbirsoft.partners.document.application.ObjectStorage;
import com.simbirsoft.partners.staffing.application.StaffingRequestAttachmentPort;
import java.util.UUID;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!auth-test")
class StaffingRequestAttachmentAdapter implements StaffingRequestAttachmentPort {
    private final LeadDocumentService documentValidation;
    private final ObjectStorage storage;
    private final StaffingRequestAttachmentJpaRepository attachments;
    StaffingRequestAttachmentAdapter(LeadDocumentService documentValidation, ObjectStorage storage,
            StaffingRequestAttachmentJpaRepository attachments) {
        this.documentValidation=documentValidation; this.storage=storage; this.attachments=attachments;
    }
    @Override @Transactional
    public Attachment upload(UUID ownerId, UUID requestId, String kind, String fileName, String contentType, byte[] content) {
        LeadDocumentService.PreparedAttachment prepared;
        try { prepared=documentValidation.prepare(fileName, contentType, content); }
        catch (ResponseStatusException failure) {
            if (failure.getStatusCode().is5xxServerError())
                throw new ResponseStatusException(failure.getStatusCode(), "Attachment could not be checked; the request was saved and no file was stored");
            throw failure;
        }
        String key="staffing-requests/"+requestId+"/"+UUID.randomUUID();
        try { storage.put(key, prepared.bytes(), prepared.contentType()); }
        catch (RuntimeException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment could not be saved"); }
        var record=new StaffingRequestAttachmentRecord(UUID.randomUUID(), requestId, ownerId, kind,
                prepared.fileName(), prepared.contentType(), key);
        try {
            var previous=attachments.findByRequestIdAndOwnerProfileIdAndKind(requestId, ownerId, kind).orElse(null);
            if (previous != null) record.id=previous.id;
            record=attachments.saveAndFlush(record);
            if (previous != null && !previous.objectKey.equals(key)) {
                try { storage.delete(previous.objectKey); } catch (RuntimeException ignored) { }
            }
            return new Attachment(record.id, record.kind, record.fileName);
        } catch (RuntimeException failure) {
            try { storage.delete(key); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }
    @Override @Transactional(readOnly=true)
    public Download download(UUID ownerId, UUID requestId, UUID attachmentId) {
        var record=attachments.findByIdAndRequestIdAndOwnerProfileId(attachmentId, requestId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        try { return new Download(record.fileName, record.contentType, storage.get(record.objectKey)); }
        catch (RuntimeException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment is temporarily unavailable"); }
    }
    @Override @Transactional(readOnly=true)
    public List<Attachment> list(UUID ownerId, UUID requestId) {
        return attachments.findAllByRequestIdAndOwnerProfileIdOrderByKindAsc(requestId, ownerId).stream()
                .map(record -> new Attachment(record.id, record.kind, record.fileName)).toList();
    }
}
