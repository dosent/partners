package com.simbirsoft.partners.partner.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import com.simbirsoft.partners.partner.domain.PartnerType;
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
        var registration = legalEntity("123-456-7890");

        ProtectedRegistrationData protectedData = protector.protect(registration);

        assertThat(new String(protectedData.encryptedPayload(), StandardCharsets.UTF_8))
                .doesNotContain("Иван Иванов", "ivan@example.test", "123-456-7890");
        assertThat(protector.reveal(protectedData)).isEqualTo(registration);
        assertThat(protectedData.taxIdentifierHash()).isNotEqualTo(registration.taxIdentifier());
    }

    @Test
    void keyedHashUsesNormalizedTaxIdentifierWithoutStoringTheClearValue() {
        var protector = new RegistrationDataProtector(new ObjectMapper(), ENCRYPTION_KEY, HASH_KEY);

        ProtectedRegistrationData first = protector.protect(legalEntity("123-456-7890"));
        ProtectedRegistrationData normalized = protector.protect(legalEntity("123 456 7890"));

        assertThat(first.taxIdentifierHash()).isEqualTo(normalized.taxIdentifierHash());
    }

    @Test
    void rejectsMissingKeysInsteadOfStoringUnencryptedData() {
        var protector = new RegistrationDataProtector(new ObjectMapper(), "", "");

        assertThatIllegalStateException()
                .isThrownBy(() -> protector.protect(legalEntity("1234567890")))
                .withMessageContaining("not configured");
    }

    private static PartnerRegistrationData legalEntity(String taxIdentifier) {
        return new PartnerRegistrationData(PartnerType.LEGAL_ENTITY, "Иван Иванов", "ivan@example.test",
                "+70000000000", "RU", "Самарская область", "ООО Пример", taxIdentifier);
    }
}
