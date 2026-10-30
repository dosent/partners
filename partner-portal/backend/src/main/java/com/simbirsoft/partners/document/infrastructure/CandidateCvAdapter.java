package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.CandidateCv;
import com.simbirsoft.partners.document.application.ObjectStorage;
import com.simbirsoft.partners.document.application.PreparedDocument;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!auth-test")
public class CandidateCvAdapter implements CandidateCv {
    private final CandidateCvJpaRepository records;
    private final ObjectStorage storage;

    public CandidateCvAdapter(CandidateCvJpaRepository records, ObjectStorage storage) {
        this.records = records; this.storage = storage;
    }

    @Override
    @Transactional
    public Metadata store(UUID candidateId, PreparedDocument file, String actor, Instant now) {
        String objectKey = "candidate-cvs/" + candidateId + "/" + UUID.randomUUID();
        byte[] content = file.bytes();
        try { storage.put(objectKey, content, file.contentType()); }
        catch (RuntimeException unavailable) {
            deleteQuietly(objectKey);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CV could not be saved");
        }
        var removed = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (removed.compareAndSet(false, true)) deleteQuietly(objectKey);
        };
        // Register before any repository call: querying or flushing may also fail the outer transaction.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) cleanup.run();
                }
            });
        }
        try {
            long version = records.findFirstByCandidateIdOrderByVersionDesc(candidateId)
                    .map(previous -> previous.version + 1).orElse(1L);
            var record = new CandidateCvRecord(UUID.randomUUID(), candidateId, version, file.fileName(),
                    file.contentType(), content.length, file.sha256(), objectKey, actor, now);
            return metadata(records.saveAndFlush(record));
        } catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Metadata> current(UUID candidateId) {
        return currentRecord(candidateId).map(CandidateCvAdapter::metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public Download download(UUID candidateId) {
        var record = currentRecord(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CV not found"));
        try { return new Download(record.fileName, record.contentType, storage.get(record.objectKey)); }
        catch (RuntimeException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CV is temporarily unavailable");
        }
    }

    private Optional<CandidateCvRecord> currentRecord(UUID candidateId) {
        return records.findFirstByCandidateIdAndStatusOrderByVersionDesc(candidateId, "CLEAN");
    }

    private static Metadata metadata(CandidateCvRecord record) {
        return new Metadata(record.id, record.version, record.fileName, record.contentType, record.size,
                record.sha256, record.checkedAt);
    }

    private void deleteQuietly(String objectKey) {
        try { storage.delete(objectKey); } catch (RuntimeException ignored) { }
    }
}
