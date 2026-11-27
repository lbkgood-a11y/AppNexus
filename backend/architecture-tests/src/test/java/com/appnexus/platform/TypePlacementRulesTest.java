package com.appnexus.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * 从真实类型反查职责落位，防止错放类型因未进入正确包而逃过检查。
 * <p>与依赖、业务和数据库检查共同执行；包名正确不代表完整用例已经符合架构。
 */
class TypePlacementRulesTest {
  private final JavaClasses classes = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.appnexus");

  /** 仅认证、会话和可信执行上下文专用入口属于安全适配器，普通业务入口不得扩入该集合。 */
  private static final Set<String> SECURITY_PROTOCOL_CONTROLLERS = Set.of(
      "AccountSessionsController", "AccountSettingsController", "MfaController",
      "SessionController", "PasswordRecoveryController", "LoginCsrfController",
      "TenantContextController", "NativeTenantContextController", "WorkspaceContextController");

  /** SQL 映射接口按实际 MyBatis 注解识别，禁止仅检查正确目录而漏掉根包或核心内的 Mapper。 */
  @Test
  void myBatisMappersHaveExplicitStoragePlacementAndNames() {
    var mappers = classes.stream().filter(type -> type.isAnnotatedWith(Mapper.class)).toList();
    assertTrue(!mappers.isEmpty(), "未扫描到生产 Mapper，不能以空集合通过门禁");
    var violations = mappers.stream().filter(type -> !type.isInterface()
        || !type.getPackageName().contains(".persistence")
        || !type.getSimpleName().endsWith("Mapper")
        || type.getSimpleName().contains("Controller")
        || type.getSimpleName().contains("Service")
        || type.getSimpleName().endsWith("SqlMapper"))
        .map(JavaClass::getName).sorted().toList();
    assertTrue(violations.isEmpty(), () -> "SQL Mapper 命名或持久化落位错误：" + violations);
  }

  /** HTTP 入口按实际注解反查；部署组合、领域、应用、存储和引擎包均不能承载业务 Controller。 */
  @Test
  void httpEntrypointsBelongToWebAdapters() {
    var controllers = classes.stream().filter(type -> type.isAnnotatedWith(RestController.class)).toList();
    assertTrue(!controllers.isEmpty(), "未扫描到生产 HTTP 入口");
    var violations = controllers.stream().filter(type -> {
      String location = type.getPackageName();
      return !(location.contains(".adapter.web") || location.contains(".platform.appapi")
          || location.contains(".platform.managementapi") || location.contains(".platform.partnerapi")
          || (location.equals("com.appnexus.platform.security")
              && SECURITY_PROTOCOL_CONTROLLERS.contains(type.getSimpleName())));
    }).map(JavaClass::getName).sorted().toList();
    assertTrue(violations.isEmpty(), () -> "HTTP 入口职责落位错误：" + violations);
  }

  /** Web 不能直接依赖任何已扫描 SQL Mapper，不能通过改包避开按 persistence 名称检查。 */
  @Test
  void businessHttpEntrypointsDoNotCallSqlMappers() {
    var violations = classes.stream().filter(type -> type.isAnnotatedWith(RestController.class))
        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
        .filter(dependency -> dependency.getTargetClass().isAnnotatedWith(Mapper.class))
        .map(Object::toString).sorted().toList();
    assertTrue(violations.isEmpty(), () -> "HTTP 入口直接依赖 SQL Mapper：" + violations);
    noClasses().that().resideInAnyPackage("..adapter.web..", "com.appnexus.platform.appapi..",
            "com.appnexus.platform.managementapi..", "com.appnexus.platform.partnerapi..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework.jdbc..", "java.sql..", "javax.sql..", "..adapter.persistence..",
            "com.appnexus.platform.persistence..", "com.appnexus.masterdata.application.personnel..",
            "com.appnexus.masterdata.domain.personnel..")
        .check(classes);
  }

  /** 人员、商品图片及模板的实际用例必须被组合产物命中，且不能重新依赖技术实现。 */
  @Test
  void migratedPersonnelAndProductUseCasesRemainIndependentOfTechnology() {
    for (String name : Set.of(
        "com.appnexus.masterdata.application.personnel.PersonDataService",
        "com.appnexus.masterdata.application.personnel.PersonnelService",
        "com.appnexus.masterdata.domain.personnel.PersonnelSchema",
        "com.appnexus.masterdata.application.product.ProductImageService",
        "com.appnexus.masterdata.application.product.ProductTemplateService",
        "com.appnexus.masterdata.application.product.ProductAttributeService",
        "com.appnexus.masterdata.application.product.ProductCatalogService",
        "com.appnexus.masterdata.application.product.ProductStructureService",
        "com.appnexus.masterdata.application.product.ProductStructureMigrationService",
        "com.appnexus.masterdata.application.product.ProductService",
        "com.appnexus.masterdata.application.product.ProductApprovalService",
        "com.appnexus.masterdata.application.product.ProductCompletenessService",
        "com.appnexus.printing.application.PrintRuntimeService",
        "com.appnexus.printing.application.PrintSceneService",
        "com.appnexus.printing.application.PrintOutputService",
        "com.appnexus.printing.core.PrintDataPolicy",
        "com.appnexus.printing.core.PrintTemplateReferences",
        "com.appnexus.workflow.application.WorkflowRuntime",
        "com.appnexus.leave.application.LeavePocService",
        "com.appnexus.platform.tenant.internal.application.invitation.InvitationService",
        "com.appnexus.platform.authorization.internal.application.template.PolicyTemplateService",
        "com.appnexus.platform.audit.internal.application.query.AuditQueryService",
        "com.appnexus.platform.configuration.internal.application.parameter.TenantParameterService",
        "com.appnexus.printing.application.BusinessPrintingService")) {
      assertTrue(classes.contain(name), () -> "生产产物缺少本批迁移用例：" + name);
      noClasses().that().haveFullyQualifiedName(name)
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "org.apache.ibatis..", "java.sql..", "javax.sql..",
              "tools.jackson..", "com.fasterxml.jackson..", "javax.imageio..", "..adapter..",
              "com.appnexus.platform.persistence..")
          .check(classes);
    }
  }

  /** Repository 是业务输出端口，数据库实现不能借接口名放在业务公开根包。 */
  @Test
  void repositoryPortsAndImplementationsExpressDifferentResponsibilities() {
    var violations = classes.stream().filter(type -> type.getSimpleName().endsWith("Repository"))
        .filter(type -> type.isInterface()
            ? !(type.getPackageName().contains(".application") || type.getPackageName().contains(".port"))
            : !type.getPackageName().contains(".persistence"))
        .map(JavaClass::getName).sorted().toList();
    assertTrue(violations.isEmpty(), () -> "仓储端口或实现落位错误：" + violations);
  }
}
