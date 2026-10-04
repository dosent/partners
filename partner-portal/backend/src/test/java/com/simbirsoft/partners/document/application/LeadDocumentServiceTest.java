package com.simbirsoft.partners.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simbirsoft.partners.document.domain.LeadAttachment;
import com.simbirsoft.partners.document.domain.LeadAttachmentStatus;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeadDocumentServiceTest {
    @Mock LeadAttachmentStore metadata;
    @Mock ObjectStorage objectStorage;
    @Mock MalwareScanner scanner;
    LeadDocumentService service;
    UUID owner = UUID.randomUUID();
    UUID lead = UUID.randomUUID();

    @BeforeEach void setUp() { service = new LeadDocumentService(metadata, objectStorage, scanner); }

    @Test
    void validatesAndScansBeforeLeadPersistenceAndComputesChecksum() {
        byte[] pdf = "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.UTF_8);
        when(scanner.scan(pdf)).thenReturn(MalwareScanner.Result.CLEAN);
        var prepared = service.prepare("brief.pdf", "application/pdf", pdf);
        assertThat(prepared.sha256()).isEqualTo("5aea7a7a5e33d66d021fd52802ceb64ac5b8f377b2be55fddca8607f093ce3ce");
        verify(metadata, never()).save(any());
        verify(objectStorage, never()).put(any(), any(), any());
    }

    @Test
    void rejectsExtensionMimeMismatchBeforeCallingScanner() {
        assertThatThrownBy(() -> service.prepare("brief.pdf", "application/octet-stream", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(400);
        verify(scanner, never()).scan(any());
    }

    @Test
    void rejectsOversizedFileBeforeCallingScanner() {
        byte[] tooLarge = new byte[LeadDocumentService.MAX_FILE_SIZE_BYTES + 1];
        assertThatThrownBy(() -> service.prepare("brief.pdf", "application/pdf", tooLarge))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(400);
        verify(scanner, never()).scan(any());
    }

    @Test
    void scanFailureFailsClosedWithoutObjectOrMetadataPersistence() {
        byte[] pdf = "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.UTF_8);
        when(scanner.scan(pdf)).thenReturn(MalwareScanner.Result.INFECTED);
        assertThatThrownBy(() -> service.prepare("brief.pdf", "application/pdf", pdf))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(422);
        verify(objectStorage, never()).put(any(), any(), any());
        verify(metadata, never()).save(any());
    }

    @Test
    void scannerUnavailableFailsClosedWithoutObjectOrMetadataPersistence() {
        byte[] pdf = "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.UTF_8);
        org.mockito.Mockito.doThrow(new ScannerUnavailableException()).when(scanner).scan(pdf);
        assertThatThrownBy(() -> service.prepare("brief.pdf", "application/pdf", pdf))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(503);
        verify(objectStorage, never()).put(any(), any(), any());
        verify(metadata, never()).save(any());
    }

    @Test
    void downloadRequiresOwnershipAndCleanStatus() {
        UUID fileId = UUID.randomUUID();
        var clean = new LeadAttachment(fileId, lead, owner, "brief.pdf", "application/pdf", 18,
                "d07b1cbb83300ef7e7fa578631d4a94afc7d833d835408fc3796fc8b011ec6f3", "object-key",
                LeadAttachmentStatus.CLEAN);
        when(metadata.findByLeadAndId(lead, fileId)).thenReturn(Optional.of(clean));
        when(objectStorage.get("object-key")).thenReturn("%PDF-1.7\nsynthetic".getBytes(StandardCharsets.UTF_8));
        assertThat(service.download(lead, fileId, owner).bytes()).startsWith("%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.download(lead, fileId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(404);
        verify(objectStorage).get("object-key");
    }

    @Test
    void quarantinedMetadataIsNeverDownloadable() {
        UUID fileId = UUID.randomUUID();
        when(metadata.findByLeadAndId(lead, fileId)).thenReturn(Optional.of(new LeadAttachment(fileId, lead, owner,
                "brief.pdf", "application/pdf", 18, "sum", "object-key", LeadAttachmentStatus.QUARANTINED)));
        assertThatThrownBy(() -> service.download(lead, fileId, owner))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(404);
        verify(objectStorage, never()).get(any());
    }
}
