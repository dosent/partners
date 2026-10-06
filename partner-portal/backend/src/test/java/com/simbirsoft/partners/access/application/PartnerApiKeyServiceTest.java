package com.simbirsoft.partners.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnerApiKeyServiceTest {
    @Test
    void createsOneTimeHighEntropySecretAndPersistsOnlyItsHash() {
        var store = new InMemoryStore();
        var service = new PartnerApiKeyService(store);
        var created = service.create(new PartnerPrincipal("yandex", "partner-1"), "CRM");

        assertThat(created.secret()).matches("ppk_[0-9a-f-]{36}_[A-Za-z0-9_-]{43}");
        var stored = store.records.getFirst();
        assertThat(stored.secretHash()).hasSize(32);
        assertThat(new String(stored.secretHash())).doesNotContain(created.secret());
        assertThat(service.authenticate(created.secret())).isPresent();
    }

    @Test
    void listAndRevokeAreOwnerScopedAndRevokedKeysCannotAuthenticate() {
        var store = new InMemoryStore();
        var service = new PartnerApiKeyService(store);
        var first = new PartnerPrincipal("yandex", "partner-1");
        var other = new PartnerPrincipal("yandex", "partner-2");
        var key = service.create(first, "CRM");

        assertThat(service.list(other)).isEmpty();
        assertThat(service.list(first)).hasSize(1);
        assertThatThrownBy(() -> service.revoke(other, key.id())).isInstanceOf(IllegalArgumentException.class);
        service.revoke(first, key.id());
        assertThat(service.authenticate(key.secret())).isEmpty();
        assertThat(service.list(first).getFirst().revokedAt()).isNotNull();
    }

    @Test
    void authenticationDoesNotRestoreKeyRevokedAfterInitialRead() {
        var store = new RevokingDuringTouchStore();
        var service = new PartnerApiKeyService(store);
        var key = service.create(new PartnerPrincipal("yandex", "partner-1"), "CRM");

        assertThat(service.authenticate(key.secret())).isEmpty();
        assertThat(store.findById(key.id()).orElseThrow().revokedAt()).isNotNull();
    }

    private static class InMemoryStore implements PartnerApiKeyStore {
        private final List<Credential> records = new ArrayList<>();
        @Override public void save(Credential credential) { records.add(credential); }
        @Override public Optional<Credential> findById(UUID id) {
            return records.stream().filter(value -> value.id().equals(id)).findFirst();
        }
        @Override public List<Credential> findByOwner(String provider, String subject) {
            return records.stream().filter(value -> value.provider().equals(provider) && value.subject().equals(subject)).toList();
        }
        @Override public void update(Credential credential) {
            var index = records.indexOf(findById(credential.id()).orElseThrow());
            records.set(index, credential);
        }
        @Override public boolean markUsedIfActive(UUID id, Instant usedAt) {
            var current=findById(id).orElseThrow();
            if(current.revokedAt()!=null) return false;
            update(new Credential(current.id(),current.provider(),current.subject(),current.name(),current.prefix(),
                    current.secretHash(),current.createdAt(),usedAt,current.revokedAt()));
            return true;
        }
    }

    private static final class RevokingDuringTouchStore extends InMemoryStore {
        @Override public boolean markUsedIfActive(UUID id, Instant usedAt) {
            var current=findById(id).orElseThrow();
            update(new Credential(current.id(),current.provider(),current.subject(),current.name(),current.prefix(),
                    current.secretHash(),current.createdAt(),current.lastUsedAt(),Instant.now()));
            return false;
        }
    }
}
