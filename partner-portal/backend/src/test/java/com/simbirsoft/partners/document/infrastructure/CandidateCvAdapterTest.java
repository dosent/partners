package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.DocumentValidationService;
import com.simbirsoft.partners.document.application.MalwareScanner;
import com.simbirsoft.partners.document.application.ObjectStorage;
import com.simbirsoft.partners.document.application.PreparedDocument;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CandidateCvAdapterTest {
    private final UUID candidateId=UUID.randomUUID();
    private final Instant checkedAt=Instant.parse("2026-10-30T08:00:00Z");
    private final CandidateCvJpaRepository records=mock(CandidateCvJpaRepository.class);
    private final MemoryStorage storage=new MemoryStorage();
    private final CandidateCvAdapter adapter=new CandidateCvAdapter(records,storage);
    private final ArrayList<CandidateCvRecord> persisted=new ArrayList<>();

    @BeforeEach void repositoryBoundary() {
        lenient().when(records.findFirstByCandidateIdOrderByVersionDesc(candidateId))
            .thenAnswer(invocation->persisted.stream().max(java.util.Comparator.comparingLong(record->record.version)));
        lenient().when(records.findFirstByCandidateIdAndStatusOrderByVersionDesc(candidateId,"CLEAN"))
            .thenAnswer(invocation->persisted.stream().filter(record->"CLEAN".equals(record.status))
                .max(java.util.Comparator.comparingLong(record->record.version)));
        lenient().when(records.saveAndFlush(any())).thenAnswer(invocation->{
            CandidateCvRecord record=invocation.getArgument(0); persisted.add(record); return record;
        });
    }

    @AfterEach void clearTransaction() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void versionsRetainPreviousFilesAndDownloadLatestCheckedVersion() {
        var first=adapter.store(candidateId,pdf("old"),"staff-1",checkedAt);
        var second=adapter.store(candidateId,pdf("new"),"staff-2",checkedAt.plusSeconds(1));
        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(second.checkedAt()).isEqualTo(checkedAt.plusSeconds(1));
        assertThat(adapter.current(candidateId)).contains(second);
        assertThat(adapter.download(candidateId).bytes()).isEqualTo("%PDF-1.4 new".getBytes());
        assertThat(storage.objects.values()).hasSize(2).anySatisfy(bytes->assertThat(bytes).isEqualTo("%PDF-1.4 old".getBytes()));
        assertThat(persisted.get(0).objectKey).isNotEqualTo(persisted.get(1).objectKey);
    }

    @Test void removesNewObjectOnRollbackAndRetainsPreviousCv() {
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        String previousKey=persisted.get(0).objectKey;
        TransactionSynchronizationManager.initSynchronization();
        adapter.store(candidateId,pdf("new"),"staff",checkedAt);
        String newKey=persisted.get(1).objectKey;
        TransactionSynchronizationManager.getSynchronizations().forEach(sync->sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(storage.objects).containsOnlyKeys(previousKey);
        assertThat(storage.deleted).containsExactly(newKey);
        assertThat(storage.objects.get(previousKey)).isEqualTo("%PDF-1.4 old".getBytes());
    }

    @Test void metadataFailureRemovesNewObjectBeforeReturningError() {
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        String previousKey=persisted.get(0).objectKey;
        doThrow(new IllegalStateException("simulated metadata failure")).when(records).saveAndFlush(any());
        TransactionSynchronizationManager.initSynchronization();
        assertThatThrownBy(()->adapter.store(candidateId,pdf("new"),"staff",checkedAt)).isInstanceOf(IllegalStateException.class);
        assertThat(storage.objects).containsOnlyKeys(previousKey);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync->sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(storage.objects).containsOnlyKeys(previousKey);
    }

    @Test void unknownTransactionOutcomePreservesBothFiles() {
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        TransactionSynchronizationManager.initSynchronization();
        adapter.store(candidateId,pdf("new"),"staff",checkedAt);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync->sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));
        assertThat(storage.objects).hasSize(2);
        assertThat(storage.deleted).isEmpty();
    }

    @Test void unavailableUploadLeavesPreviousCvUntouched() {
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        var previous=adapter.current(candidateId);
        storage.failUpload=true;
        assertThatThrownBy(()->adapter.store(candidateId,pdf("new"),"staff",checkedAt))
            .isInstanceOfSatisfying(ResponseStatusException.class,exception->assertThat(exception.getStatusCode().value()).isEqualTo(503));
        assertThat(adapter.current(candidateId)).isEqualTo(previous);
        assertThat(storage.objects).hasSize(1);
    }

    @Test void uploadFailureAfterPartialWriteRemovesOnlyTheAttemptedNewObject() {
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        var previous=adapter.current(candidateId);
        String previousKey=persisted.get(0).objectKey;
        storage.failAfterWrite=true;
        assertThatThrownBy(()->adapter.store(candidateId,pdf("new"),"staff",checkedAt))
            .isInstanceOfSatisfying(ResponseStatusException.class,exception->assertThat(exception.getStatusCode().value()).isEqualTo(503));
        assertThat(adapter.current(candidateId)).isEqualTo(previous);
        assertThat(storage.objects).containsOnlyKeys(previousKey);
    }

    @Test void unavailableDownloadReturnsSafeTemporaryError() {
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        storage.objects.clear();
        assertThatThrownBy(()->adapter.download(candidateId))
            .isInstanceOfSatisfying(ResponseStatusException.class,exception->assertThat(exception.getStatusCode().value()).isEqualTo(503));
    }

    @Test void missingOrUncheckedFileIsNotDownloadable() {
        assertThat(adapter.current(candidateId)).isEmpty();
        assertThatThrownBy(()->adapter.download(candidateId))
            .isInstanceOfSatisfying(ResponseStatusException.class,exception->assertThat(exception.getStatusCode().value()).isEqualTo(404));
        adapter.store(candidateId,pdf("old"),"staff",checkedAt);
        persisted.get(0).status="QUARANTINED";
        assertThat(adapter.current(candidateId)).isEmpty();
        assertThatThrownBy(()->adapter.download(candidateId))
            .isInstanceOfSatisfying(ResponseStatusException.class,exception->assertThat(exception.getStatusCode().value()).isEqualTo(404));
    }

    private PreparedDocument pdf(String content) {
        return new DocumentValidationService(bytes->MalwareScanner.Result.CLEAN)
            .prepare(content+".pdf","application/pdf",("%PDF-1.4 "+content).getBytes());
    }

    private static class MemoryStorage implements ObjectStorage {
        final Map<String,byte[]> objects=new HashMap<>();
        final ArrayList<String> deleted=new ArrayList<>();
        boolean failUpload;
        boolean failAfterWrite;
        public void put(String key,byte[] content,String mime) {
            if(failUpload) throw new IllegalStateException("simulated storage failure");
            objects.put(key,content.clone());
            if(failAfterWrite) throw new IllegalStateException("simulated timeout after upload");
        }
        public byte[] get(String key) {
            if(!objects.containsKey(key)) throw new IllegalStateException("missing object");
            return objects.get(key).clone();
        }
        public void delete(String key) { objects.remove(key); deleted.add(key); }
    }
}
