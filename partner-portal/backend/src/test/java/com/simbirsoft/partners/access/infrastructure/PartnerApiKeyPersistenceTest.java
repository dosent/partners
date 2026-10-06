package com.simbirsoft.partners.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.simbirsoft.partners.access.application.PartnerApiKeyStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PartnerApiKeyPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired PartnerApiKeyJpaRepository repository;

    @Test @Transactional void storesOnlyCredentialHashAndPersistsRevocation() {
        var id=UUID.randomUUID(); var created=Instant.now(); byte[] hash=new byte[32]; hash[0]=7;
        var saved=repository.saveAndFlush(PartnerApiKeyRecord.from(new PartnerApiKeyStore.Credential(id,
                "yandex", "partner-test", "crm", "ppk_"+id, hash, created, null, null)));
        assertThat(saved.toCredential().secretHash()).containsExactly(hash);
        saved.update(new PartnerApiKeyStore.Credential(id, "yandex", "partner-test", "crm", "ppk_"+id,
                hash, created, created.plusSeconds(5), created.plusSeconds(6)));
        repository.flush();
        assertThat(repository.findById(id).orElseThrow().toCredential().revokedAt()).isEqualTo(created.plusSeconds(6));
    }

    @Test @Transactional void lastUsedUpdateCannotChangeARevokedKeyBackToActive() {
        var id=UUID.randomUUID(); var created=Instant.now(); byte[] hash=new byte[32]; hash[0]=7;
        var row=repository.saveAndFlush(PartnerApiKeyRecord.from(new PartnerApiKeyStore.Credential(id,
                "yandex", "partner-test", "crm", "ppk_"+id, hash, created, null, null)));
        row.update(new PartnerApiKeyStore.Credential(id,"yandex","partner-test","crm","ppk_"+id,
                hash,created,null,created.plusSeconds(2)));
        repository.flush();

        assertThat(repository.markUsedIfActive(id,created.plusSeconds(3))).isZero();
        assertThat(repository.findById(id).orElseThrow().toCredential().revokedAt()).isEqualTo(created.plusSeconds(2));
    }
}
