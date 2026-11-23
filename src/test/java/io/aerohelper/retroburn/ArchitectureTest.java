// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The layering rules from {@code docs/architecture/HLD.md} section 1, checked on every build.
 *
 * <p>Rules about packages that have no classes yet still pass, so they guard future code from the
 * start. If a rule has to change, change the HLD in the same commit.
 */
class ArchitectureTest {

    private static final String ROOT = "io.aerohelper.retroburn";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @ParameterizedTest
    @ValueSource(strings = {"linux", "windows", "simulated"})
    void platformImplementationsAreUsedOnlyByThemselvesAndTheCompositionRoot(String platform) {
        String platformPackage = ROOT + ".platform." + platform + "..";
        classes()
                .that()
                .resideInAPackage(platformPackage)
                .should()
                .onlyBeAccessed()
                .byClassesThat()
                .resideInAnyPackage(platformPackage, ROOT + ".app..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void userInterfaceIsUsedOnlyByTheApplicationShell() {
        classes()
                .that()
                .resideInAPackage(ROOT + ".ui..")
                .should()
                .onlyBeAccessed()
                .byClassesThat()
                .resideInAnyPackage(ROOT + ".ui..", ROOT + ".app..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void nothingDependsOnTheApplicationShell() {
        classes()
                .that()
                .resideInAPackage(ROOT + ".app..")
                .should()
                .onlyBeAccessed()
                .byClassesThat()
                .resideInAPackage(ROOT + ".app..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void javaFxIsUsedOnlyInViewPackages() {
        noClasses()
                .that()
                .resideOutsideOfPackages(ROOT + ".app..", ROOT + ".ui..", ROOT + ".theme..", ROOT + ".design..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("javafx..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void engineContractsDependOnlyOnCommonCode() {
        classes()
                .that()
                .resideInAPackage(ROOT + ".engine..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(ROOT + ".engine..", ROOT + ".common..", "java..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void commonCodeDependsOnNothingElseInRetroBurn() {
        noClasses()
                .that()
                .resideInAPackage(ROOT + ".common..")
                .should()
                .dependOnClassesThat()
                .resideOutsideOfPackages(ROOT + ".common..", "java..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void runtimeExecIsNeverUsed() {
        noClasses()
                .should()
                .callMethodWhere(target(name("exec")).and(target(owner(assignableTo(Runtime.class)))))
                .because("external programs must be started with ProcessBuilder and an argument list")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void processesAreStartedOnlyByTheToolsPackage() {
        noClasses()
                .that()
                .resideOutsideOfPackage(ROOT + ".tools..")
                .should()
                .dependOnClassesThat()
                .areAssignableTo(ProcessBuilder.class)
                .because("all external programs run through the tools package's process runner")
                .check(PRODUCTION_CLASSES);
    }
}
