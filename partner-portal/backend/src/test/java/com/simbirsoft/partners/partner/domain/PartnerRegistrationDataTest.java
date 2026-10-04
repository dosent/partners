package com.simbirsoft.partners.partner.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PartnerRegistrationDataTest {
    @Test
    void individualDoesNotRequireOrAcceptBusinessRequisites() {
        assertThatCode(() -> new PartnerRegistrationData(
                PartnerType.INDIVIDUAL, "Иван Иванов", "ivan@example.test", "+70000000000",
                "RU", "Самарская область", null, null))
                .doesNotThrowAnyException();

        assertThatIllegalArgumentException().isThrownBy(() -> new PartnerRegistrationData(
                PartnerType.INDIVIDUAL, "Иван Иванов", "ivan@example.test", "+70000000000",
                "RU", "Самарская область", "ООО Пример", null));
    }

    @Test
    void soleProprietorRequiresTaxIdentifierButNotCompanyName() {
        assertThatCode(() -> new PartnerRegistrationData(
                PartnerType.SOLE_PROPRIETOR, "Иван Иванов", "ivan@example.test", "+70000000000",
                "RU", "Самарская область", null, "100000000074"))
                .doesNotThrowAnyException();

        assertThatIllegalArgumentException().isThrownBy(() -> new PartnerRegistrationData(
                PartnerType.SOLE_PROPRIETOR, "Иван Иванов", "ivan@example.test", "+70000000000",
                "RU", "Самарская область", null, null));
    }

    @Test
    void legalEntityRequiresCompanyNameAndTaxIdentifier() {
        assertThatCode(() -> new PartnerRegistrationData(
                PartnerType.LEGAL_ENTITY, "Иван Иванов", "ivan@example.test", "+70000000000",
                "RU", "Самарская область", "ООО Пример", "1000000002"))
                .doesNotThrowAnyException();

        assertThatIllegalArgumentException().isThrownBy(() -> new PartnerRegistrationData(
                PartnerType.LEGAL_ENTITY, "Иван Иванов", "ivan@example.test", "+70000000000",
                "RU", "Самарская область", null, "1000000002"));
    }
}
