package com.simbirsoft.partners.architecture;

import com.simbirsoft.partners.lead.infrastructure.LeadRecord;
import com.simbirsoft.partners.lead.infrastructure.LeadStore;
import com.simbirsoft.partners.partner.application.UsesLeadEntity;
import com.simbirsoft.partners.partner.application.UsesLeadRepository;
import com.simbirsoft.partners.partner.application.UsesOwnPersistence;
import com.simbirsoft.partners.partner.infrastructure.PartnerRecord;
import com.simbirsoft.partners.partner.infrastructure.PartnerStore;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulePersistenceArchitectureTest {
    @Test
    void productionCodeDoesNotUsePersistenceTypesFromOtherModules() {
        var productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.simbirsoft.partners");

        ModulePersistenceRules.NO_CROSS_MODULE_PERSISTENCE.check(productionClasses);
    }

    @Test
    void rejectsEntityFromAnotherModule() {
        var classes = new ClassFileImporter().importClasses(UsesLeadEntity.class, LeadRecord.class);

        var violation = assertThrows(AssertionError.class,
                () -> ModulePersistenceRules.NO_CROSS_MODULE_PERSISTENCE.check(classes));
        assertTrue(violation.getMessage().contains("LeadRecord"));
    }

    @Test
    void rejectsSpringDataRepositoryFromAnotherModule() {
        var classes = new ClassFileImporter().importClasses(
                UsesLeadRepository.class, LeadStore.class, LeadRecord.class);

        var violation = assertThrows(AssertionError.class,
                () -> ModulePersistenceRules.NO_CROSS_MODULE_PERSISTENCE.check(classes));
        assertTrue(violation.getMessage().contains("LeadStore"));
    }

    @Test
    void permitsEntityAndRepositoryFromOwnModule() {
        var classes = new ClassFileImporter().importClasses(
                UsesOwnPersistence.class, PartnerRecord.class, PartnerStore.class);

        assertDoesNotThrow(() -> ModulePersistenceRules.NO_CROSS_MODULE_PERSISTENCE.check(classes));
    }
}
