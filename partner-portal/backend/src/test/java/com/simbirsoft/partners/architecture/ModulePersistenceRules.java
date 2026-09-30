package com.simbirsoft.partners.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class ModulePersistenceRules {
    private static final String MODULE_ROOT = "com.simbirsoft.partners.";
    private static final Set<String> MODULES = Set.of(
            "access", "partner", "lead", "staffing", "candidate", "finance",
            "document", "notification", "localization", "audit", "integration");

    private ModulePersistenceRules() {
    }

    static final ArchRule NO_CROSS_MODULE_PERSISTENCE = classes()
            .that().resideInAPackage("com.simbirsoft.partners..")
            .should(new ArchCondition<>("avoid persistence types owned by another module") {
                @Override
                public void check(JavaClass source, ConditionEvents events) {
                    String sourceModule = moduleOf(source);
                    if (sourceModule == null) {
                        return;
                    }
                    source.getDirectDependenciesFromSelf().forEach(dependency -> {
                        JavaClass target = dependency.getTargetClass();
                        String targetModule = moduleOf(target);
                        if (targetModule != null && !sourceModule.equals(targetModule)
                                && isPersistenceType(target)) {
                            events.add(SimpleConditionEvent.violated(source, dependency.getDescription()));
                        }
                    });
                }
            });

    private static boolean isPersistenceType(JavaClass target) {
        return target.isAnnotatedWith(Entity.class) || target.isAssignableTo(Repository.class);
    }

    private static String moduleOf(JavaClass type) {
        String packageName = type.getPackageName();
        if (!packageName.startsWith(MODULE_ROOT)) {
            return null;
        }
        String relative = packageName.substring(MODULE_ROOT.length());
        int separator = relative.indexOf('.');
        String module = separator < 0 ? relative : relative.substring(0, separator);
        return MODULES.contains(module) ? module : null;
    }
}
