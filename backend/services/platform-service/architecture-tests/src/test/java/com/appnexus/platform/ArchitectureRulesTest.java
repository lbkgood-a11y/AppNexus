package com.appnexus.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    @Test
    void domainCodeDoesNotDependOnAdaptersOrFrameworks() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.platform");
        noClasses().that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter..", "org.springframework..", "org.mybatis..", "io.lettuce..")
                .check(classes);
    }

    @Test
    void modulesDoNotReachIntoOtherModulesInternalPackages() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.platform");
        noClasses().that().resideOutsideOfPackage("com.appnexus.platform.identity..")
                .should().dependOnClassesThat().resideInAPackage("com.appnexus.platform.identity.internal..")
                .check(classes);
    }
}
