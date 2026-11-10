package com.appnexus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** 组合部署仍执行服务边界检查；启动类是唯一允许装配双方实现的例外。 */
class WorkflowBoundaryTest {
  @Test
  void workflowDoesNotDependOnPlatformImplementation() {
    var classes = new ClassFileImporter().importPackages("com.appnexus.workflow");
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.appnexus.platform..")
        .check(classes);
  }

  @Test
  void platformDoesNotReachIntoWorkflowImplementation() {
    var classes = new ClassFileImporter().importPackages("com.appnexus.platform");
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName("com.appnexus.platform.PlatformApplication")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.appnexus.workflow..")
        .check(classes);
  }

  @Test
  void coreAndContractsRemainIndependentOfFrameworks() {
    var classes =
        new ClassFileImporter()
            .importPackages("com.appnexus.workflow.core", "com.appnexus.contracts.workflow");
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.flowable..",
            "org.springframework..",
            "com.appnexus.platform..",
            "com.appnexus.workflow.adapter..")
        .check(classes);
  }

  @Test
  void onlyFlowableAdapterMayReferenceEngineClasses() {
    var classes =
        new ClassFileImporter().importPackages("com.appnexus.workflow", "com.appnexus.platform");
    noClasses()
        .that()
        .resideOutsideOfPackage("com.appnexus.workflow.adapter.flowable..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.flowable..")
        .check(classes);
  }

  @Test
  void contractsDoNotExposeServiceImplementations() {
    var classes = new ClassFileImporter().importPackages("com.appnexus.contracts.workflow");
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.appnexus.workflow..", "com.appnexus.platform..")
        .check(classes);
  }
}
