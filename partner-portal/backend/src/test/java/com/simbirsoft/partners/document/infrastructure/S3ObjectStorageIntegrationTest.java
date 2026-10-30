package com.simbirsoft.partners.document.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class S3ObjectStorageIntegrationTest {
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", "e2e-storage-user")
            .withEnv("MINIO_ROOT_PASSWORD", "e2e-storage-password")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Test
    void uploadsDownloadsAndDeletesTypedDocumentsUsingRealS3Signatures() {
        var storage = new S3ObjectStorage("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000),
                "attachments-" + UUID.randomUUID(), "e2e-storage-user", "e2e-storage-password", "us-east-1");
        for (String mime : new String[] {"application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}) {
            String key = "staffing-requests/" + UUID.randomUUID() + "/synthetic file";
            byte[] content = ("Synthetic attachment " + mime).getBytes(StandardCharsets.UTF_8);
            try {
                storage.put(key, content, mime);
                assertThat(storage.get(key)).isEqualTo(content);
            } finally {
                storage.delete(key);
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.get(key))
                    .isInstanceOf(S3ObjectStorage.StorageFailure.class)
                    .hasMessageContaining("404");
        }
    }
}
