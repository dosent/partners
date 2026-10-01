package com.simbirsoft.partners.architecture;

import com.simbirsoft.architecture.fixtures.lead.LeadRecord;
import com.simbirsoft.architecture.fixtures.lead.LeadStore;
import com.simbirsoft.partners.partner.application.UsesLeadEntity;
import com.simbirsoft.partners.partner.application.UsesLeadRepository;
import com.simbirsoft.partners.partner.application.UsesOwnPersistence;
import com.simbirsoft.architecture.fixtures.partner.PartnerRecord;
import com.simbirsoft.architecture.fixtures.partner.PartnerStore;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModulePersistenceArchitectureTest {
    @Test
    void productionCodeDoesNotUsePersistenceTypesFromOtherModules() {
        var productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.simbirsoft.partners");

        ModulePersistenceRules.NO_CROSS_MODULE_PERSISTENCE.check(productionClasses);
        ModulePersistenceRules.NO_STORED_PROCEDURE_CALLS.check(productionClasses);
    }

    @Test
    void accessApplicationDoesNotDependOnApiOrInfrastructureAdapters() {
        var productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.simbirsoft.partners.access.application");

        noClasses().that().resideInAPackage("..access.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..access.api..", "..access.infrastructure..")
                .check(productionClasses);
    }

    @Test
    void accessApiDoesNotDependOnInfrastructureAdapters() {
        var productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.simbirsoft.partners.access.api");

        noClasses().that().resideInAPackage("..access.api..")
                .should().dependOnClassesThat().resideInAnyPackage("..access.infrastructure..")
                .check(productionClasses);
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

    @Test
    void rejectsSimpleJdbcCallDependency() {
        var classes = new ClassFileImporter().importClasses(UsesSimpleJdbcCall.class);
        assertThrows(AssertionError.class,
                () -> ModulePersistenceRules.NO_STORED_PROCEDURE_CALLS.check(classes));
    }

    private static class UsesSimpleJdbcCall {
        SimpleJdbcCall call;
    }
}
