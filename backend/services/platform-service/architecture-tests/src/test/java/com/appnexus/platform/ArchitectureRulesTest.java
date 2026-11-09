package com.appnexus.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
import com.appnexus.platform.security.EndpointPolicy;
import com.appnexus.platform.security.EndpointOperation;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * 所有正式 HTTP Controller 必须显式登记安全域；注解不能替代运行时权限校验，
     * 但可阻止新增接口在未确定平台、租户或本人边界时静默进入构建产物。
     */
    @Test
    void everyRestControllerDeclaresEndpointPolicy() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.platform.security");
        var missing = classes.stream()
            .filter(type -> type.isAnnotatedWith(RestController.class))
            .filter(type -> !type.isAnnotatedWith(EndpointPolicy.class))
            .map(type -> type.getName()).sorted().toList();
        assertTrue(missing.isEmpty(), () -> "未登记安全域的 HTTP Controller: " + missing);
    }

    /** 混合承载平台与租户入口的授权 Controller 必须逐方法登记稳定权限码。 */
    @Test
    void authorizationControllersDeclareEveryHttpOperation() {
        var classes = new ClassFileImporter().importPackages("com.appnexus.platform.security");
        var missing = classes.stream()
            .filter(type -> type.getSimpleName().equals("RoleManagementController") ||
                type.getSimpleName().equals("AuthorizationManagementController") ||
                type.getSimpleName().equals("PlatformAdministrationController") ||
                type.getSimpleName().equals("MenuManagementController") ||
                type.getSimpleName().equals("DictionaryManagementController") ||
                type.getSimpleName().equals("OrganizationDomainController") ||
                type.getSimpleName().equals("OrganizationController") ||
                type.getSimpleName().equals("OrganizationGrantController"))
            .flatMap(type -> type.getMethods().stream())
            .filter(method -> method.getAnnotations().stream().anyMatch(annotation ->
                annotation.getRawType().getPackageName().equals("org.springframework.web.bind.annotation") &&
                annotation.getRawType().getSimpleName().endsWith("Mapping")))
            .filter(method -> !method.isAnnotatedWith(EndpointOperation.class))
            .map(method -> method.getFullName()).sorted().toList();
        assertTrue(missing.isEmpty(), () -> "未登记稳定权限码的授权端点: " + missing);
    }
}
