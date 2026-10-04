package com.simbirsoft.partners.partner.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.domain.PartnerType;
import com.simbirsoft.partners.partner.application.PartnerProfileContactsProtector.ProfileContacts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RegistrationDataProtectorTest {
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String HASH_KEY = Base64.getEncoder().encodeToString("test-hash-key-that-is-long-enough".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsTheWholeRegistrationPayloadAndCanRestoreIt() {
        var protector = new RegistrationDataProtector(new ObjectMapper(), ENCRYPTION_KEY, HASH_KEY);
        var registration = legalEntity("1000000002");

        ProtectedRegistrationData protectedData = protector.protect(registration);

        assertThat(new String(protectedData.encryptedPayload(), StandardCharsets.UTF_8))
                .doesNotContain("Иван Иванов", "ivan@example.test", "1000000002");
        assertThat(protector.reveal(protectedData)).isEqualTo(registration);
        assertThat(protectedData.taxIdentifierHash()).isNotEqualTo(registration.taxIdentifier());
    }

    @Test
    void keyedHashIsDeterministicForAValidatedTaxIdentifier() {
        var protector = new RegistrationDataProtector(new ObjectMapper(), ENCRYPTION_KEY, HASH_KEY);

        ProtectedRegistrationData first = protector.protect(legalEntity("1000000002"));
        ProtectedRegistrationData repeated = protector.protect(legalEntity("1000000002"));

        assertThat(first.taxIdentifierHash()).isEqualTo(repeated.taxIdentifierHash());
    }

    @Test
    void rejectsMissingKeysInsteadOfStoringUnencryptedData() {
        var protector = new RegistrationDataProtector(new ObjectMapper(), "", "");

        assertThatIllegalStateException()
                .isThrownBy(() -> protector.protect(legalEntity("1000000002")))
                .withMessageContaining("not configured");
    }

    @Test
    void encryptsEditableProfileContactsWithSeparateAuthenticatedContext() {
        var protector = new RegistrationDataProtector(new ObjectMapper(), ENCRYPTION_KEY, HASH_KEY);
        var contacts = new ProfileContacts("Иван Иванов", "ivan@example.test", "+79990000000");

        byte[] encrypted = protector.protect(contacts);

        assertThat(new String(encrypted, StandardCharsets.UTF_8))
                .doesNotContain(contacts.fullName(), contacts.email(), contacts.phone());
        assertThat(protector.reveal(encrypted)).isEqualTo(contacts);
        byte[] tampered = encrypted.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThatIllegalStateException().isThrownBy(() -> protector.reveal(tampered));
    }

    private static PartnerRegistrationData legalEntity(String taxIdentifier) {
        return new PartnerRegistrationData(PartnerType.LEGAL_ENTITY, "Иван Иванов", "ivan@example.test",
                "+70000000000", "RU", "Самарская область", "ООО Пример", taxIdentifier);
    }
}
