package com.simbirsoft.partners.localization.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegistrationContentDraftRequestTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsRegistrationAndOptionalCandidateCatalogueTexts() {
        assertThat(validator.validate(requestWithTexts(227))).isEmpty();
    }

    @Test
    void acceptsExactlyFiveHundredTexts() {
        assertThat(validator.validate(requestWithTexts(500))).isEmpty();
    }

    @Test
    void rejectsMoreThanFiveHundredTexts() {
        assertThat(validator.validate(requestWithTexts(501)))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("strings"));
    }

    @Test
    void stillRejectsOversizedIndividualText() {
        var request = new RegistrationContentDraftRequest(0L,
                Map.of("registration.title", "x".repeat(12001)), Map.of(), "", "", "", "");
        assertThat(validator.validate(request)).isNotEmpty();
    }

    private RegistrationContentDraftRequest requestWithTexts(int count) {
        Map<String, String> strings = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            strings.put("synthetic.label." + index, "Искусственная подпись " + index);
        }
        return new RegistrationContentDraftRequest(0L, strings, Map.of(), "", "", "", "");
    }
}
