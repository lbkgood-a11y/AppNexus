package com.appnexus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** 组合部署仍执行服务边界检查；启动类是唯一允许装配双方实现的例外。 */
class ApprovalBoundaryTest {
    @Test
    void approvalDoesNotDependOnPlatformImplementation() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.approval");
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.appnexus.platform..").check(classes);
    }

    @Test
    void platformDoesNotReachIntoApprovalImplementation() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.platform");
        noClasses().that().doNotHaveFullyQualifiedName("com.appnexus.platform.PlatformApplication")
                .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.approval..")
                .check(classes);
    }

    @Test
    void coreAndContractsRemainIndependentOfFrameworks() {
        var classes = new ClassFileImporter().importPackages(
                "com.appnexus.approval.core", "com.appnexus.contracts.approval");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "org.flowable..", "org.springframework..", "com.appnexus.platform..",
                "com.appnexus.approval.adapter..").check(classes);
    }

    @Test
    void onlyFlowableAdapterMayReferenceEngineClasses() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.approval", "com.appnexus.platform");
        noClasses().that().resideOutsideOfPackage("com.appnexus.approval.adapter.flowable..")
                .should().dependOnClassesThat().resideInAnyPackage("org.flowable..").check(classes);
    }

    @Test
    void contractsDoNotExposeServiceImplementations() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.contracts.approval");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.appnexus.approval..", "com.appnexus.platform..").check(classes);
    }
}
