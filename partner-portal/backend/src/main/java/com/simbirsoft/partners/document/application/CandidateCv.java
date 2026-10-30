package com.simbirsoft.partners.document.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Internal CV storage; the calling application service must authorize candidate access. */
public interface CandidateCv {
    Metadata store(UUID candidateId, PreparedDocument file, String actor, Instant now);
    Optional<Metadata> current(UUID candidateId);
    Download download(UUID candidateId);

    @Schema(description = "Метаданные текущей проверенной версии CV без ключа объектного хранилища")
    record Metadata(UUID id, long version, String fileName, String contentType, long size,
            String sha256, Instant checkedAt) { }

    record Download(String fileName, String contentType, byte[] bytes) {
        public Download { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
