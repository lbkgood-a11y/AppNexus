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
                type.getSimpleName().equals("OrganizationGrantController") ||
                type.getSimpleName().equals("IntegrationClientController") ||
                type.getSimpleName().equals("TaskManagementController") ||
                type.getSimpleName().equals("OrganizationSummaryController") ||
                type.getSimpleName().equals("PolicyTemplateController") ||
                type.getSimpleName().equals("AuditQueryController"))
            .flatMap(type -> type.getMethods().stream())
            .filter(method -> method.getAnnotations().stream().anyMatch(annotation ->
                annotation.getRawType().getPackageName().equals("org.springframework.web.bind.annotation") &&
                annotation.getRawType().getSimpleName().endsWith("Mapping")))
            .filter(method -> !method.isAnnotatedWith(EndpointOperation.class))
            // 本人平台资格探测不是 tenant:read，后续目录/写入仍独立鉴权。
            .filter(method -> !(method.getOwner().getSimpleName().equals("PlatformAdministrationController")&&method.getName().equals("access")))
            .map(method -> method.getFullName()).sorted().toList();
        assertTrue(missing.isEmpty(), () -> "未登记稳定权限码的授权端点: " + missing);
    }
    /** 所有平台映射均须登记操作，新 Controller 不能通过遗漏类名清单绕过检查。 */
    @Test void everyPlatformMappingDeclaresOperation(){
        var classes=new ClassFileImporter().importPackages("com.appnexus.platform.security");
        var missing=new java.util.ArrayList<String>();
        for(var type:classes)if(type.isAnnotatedWith(RestController.class)){
            String prefix="";
            if(type.isAnnotatedWith(org.springframework.web.bind.annotation.RequestMapping.class)){
                var mapping=type.getAnnotationOfType(org.springframework.web.bind.annotation.RequestMapping.class);
                prefix=String.join("",mapping.value())+String.join("",mapping.path());
            }
            for(var method:type.getMethods())for(var annotation:method.getAnnotations()){
                if(!annotation.getRawType().getPackageName().equals("org.springframework.web.bind.annotation")||!annotation.getRawType().getSimpleName().endsWith("Mapping"))continue;
                Object value=annotation.getProperties().get("value");
                String paths=value instanceof Object[] array?java.util.Arrays.toString(array):String.valueOf(value);
                boolean platform=prefix.contains("/bff/platform")||paths.contains("/bff/platform");
                if(platform&&!method.isAnnotatedWith(EndpointOperation.class)&&!(type.getSimpleName().equals("PlatformAdministrationController")&&method.getName().equals("access")))missing.add(method.getFullName());
                if(platform&&method.isAnnotatedWith(EndpointOperation.class)){
                    var operation=method.getAnnotationOfType(EndpointOperation.class);
                    if(operation.value().startsWith("access.tenant.")&&operation.platformPermission().isBlank())missing.add(method.getFullName()+" 缺少平台代管权限");
                }
            }
        }
        assertTrue(missing.isEmpty(),()->"平台操作缺少权限登记："+missing);
    }
}
