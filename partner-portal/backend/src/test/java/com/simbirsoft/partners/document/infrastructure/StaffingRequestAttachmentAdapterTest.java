package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.LeadDocumentService;
import com.simbirsoft.partners.document.application.ObjectStorage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StaffingRequestAttachmentAdapterTest {
    private final LeadDocumentService validation = mock(LeadDocumentService.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final StaffingRequestAttachmentJpaRepository attachments = mock(StaffingRequestAttachmentJpaRepository.class);
    private final StaffingRequestAttachmentAdapter adapter = new StaffingRequestAttachmentAdapter(validation, storage, attachments);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deletesReplacedObjectOnlyAfterTheDatabaseTransactionCommits() {
        UUID owner = UUID.randomUUID(), request = UUID.randomUUID(), attachment = UUID.randomUUID();
        String oldKey = "staffing-requests/" + request + "/old";
        var previous = new StaffingRequestAttachmentRecord(attachment, request, owner, "cv", "old.pdf", "application/pdf", oldKey);
        when(attachments.findByRequestIdAndOwnerProfileIdAndKind(request, owner, "cv")).thenReturn(Optional.of(previous));
        when(validation.prepare("new.pdf", "application/pdf", new byte[] {1}))
                .thenReturn(new LeadDocumentService.PreparedAttachment("new.pdf", "application/pdf", new byte[] {1}, "hash"));
        when(attachments.saveAndFlush(any())).thenAnswer(invocation -> {
            StaffingRequestAttachmentRecord replacement = invocation.getArgument(0);
            previous.objectKey = replacement.objectKey;
            previous.fileName = replacement.fileName;
            return previous;
        });

        TransactionSynchronizationManager.initSynchronization();
        adapter.upload(owner, request, "cv", "new.pdf", "application/pdf", new byte[] {1});

        verify(storage, never()).delete(oldKey);
        var synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        verify(storage).delete(oldKey);
    }

    @Test
    void removesNewObjectAfterTransactionRollback() {
        UUID owner = UUID.randomUUID(), request = UUID.randomUUID();
        when(attachments.findByRequestIdAndOwnerProfileIdAndKind(request, owner, "cv")).thenReturn(Optional.empty());
        when(validation.prepare("new.pdf", "application/pdf", new byte[] {1}))
                .thenReturn(new LeadDocumentService.PreparedAttachment("new.pdf", "application/pdf", new byte[] {1}, "hash"));
        when(attachments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        adapter.upload(owner, request, "cv", "new.pdf", "application/pdf", new byte[] {1});
        var synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(argThat(key -> key.startsWith("staffing-requests/" + request + "/")));
    }
}
