package com.appnexus.platform;


import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 扫描组合应用包含的全部自有服务，约束领域、核心与已迁移用例的依赖方向。
 * 平台 application 是启动装配模块，不冒充领域用例层；存量未迁移服务不因本测试通过而视为完成。
 */
class ServiceLayerBoundaryTest {
  /** 中立SQL范围元数据不能反向引入平台领域、授权决策或框架实现。 */
  @Test void mapperScopeMetadataRemainsIndependentOfBusinessAndFrameworks(){
    assertTrue(classes.contain("com.appnexus.persistence.mybatis.TenantAggregateScoped"));
    noClasses().that().resideInAPackage("com.appnexus.persistence.mybatis..")
        .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.platform..","com.appnexus.masterdata..","org.springframework..","org.apache.ibatis..","java.sql..")
        .check(classes);
  }
  /** 已迁移存储不能重新引用JDBC客户端；此检查不把未迁移存量或权限覆盖缺口标记为通过。 */
  @Test void migratedMyBatisStoresDoNotFallBackToJdbc(){
    for(String name:java.util.List.of(
        "com.appnexus.platform.persistence.businessobject.configuration.MyBatisConfigurationStore",
        "com.appnexus.platform.persistence.businessobject.coding.MyBatisCodingStore",
        "com.appnexus.platform.persistence.configuration.MyBatisBusinessValueCatalog",
        "com.appnexus.masterdata.adapter.persistence.organization.ScopedOrganizationSummary",
        "com.appnexus.platform.persistence.platformscope.TransactionalAuthorityEvents",
        "com.appnexus.masterdata.adapter.persistence.MyBatisUnitConversionRepository",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductRepository",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductCompletenessFacts",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductSnapshot",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductHistory",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductListQueries")){
      assertTrue(classes.contain(name),()->"缺少已迁移存储："+name);
      noClasses().that().haveFullyQualifiedName(name).should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..").check(classes);
    }
  }
  /** 业务对象不能重新增加工作流专用处理器，审批接入只能由公共对象桥接生成。 */
  @Test void businessObjectsDoNotImplementWorkflowHandlers() {
    noClasses().that().resideInAnyPackage("com.appnexus.leave..", "com.appnexus.masterdata..")
        .should().implement(com.appnexus.contracts.workflow.WorkflowBusiness.class).check(classes);
  }
  /** 权限规则和公开契约保持纯模型，不能把SQL、JSON、组织私表或业务服务实现移入权限核心。 */
  @Test void authorizationModelRemainsCohesiveAndTechnologyIndependent() {
    assertTrue(classes.contain("com.appnexus.platform.authorization.DataGrantRules"));
    noClasses().that().resideInAnyPackage("com.appnexus.platform.authorization..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "org.apache.ibatis..", "java.sql..", "com.fasterxml.jackson..",
            "tools.jackson..", "com.appnexus.platform.persistence..", "com.appnexus.platform.security..",
            "com.appnexus.masterdata..", "com.appnexus.masterdata.organization.personnel..")
        .check(classes);
  }
  private final JavaClasses classes = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.appnexus");

  /** 仓储引用链必须实际进入扫描，同时只依赖主数据的公开入口。 */
  @Test void inboundReferencesUsePublicMasterDataBoundary() {
    assertTrue(classes.contain("com.appnexus.bulkwarehousing.adapter.integration.MasterDataInboundReceiptReferences"));
    noClasses().that().resideInAPackage("com.appnexus.bulkwarehousing..")
        .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.masterdata.application..",
            "com.appnexus.masterdata.domain..", "com.appnexus.masterdata.adapter..",
            "com.appnexus.masterdata.bootstrap..")
        .check(classes);
    assertTrue(classes.contain("com.appnexus.bulkwarehousing.application.ResolveInboundReceiptReferences"));
    noClasses().that().resideInAnyPackage("com.appnexus.bulkwarehousing.api..", "com.appnexus.bulkwarehousing.application..")
        .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.masterdata..",
            "com.appnexus.bulkwarehousing.adapter..", "org.springframework..").check(classes);
  }

  /** 扫描保留的仓储规则包，并检查业务包之间的依赖。 */
  @Test void warehousingDomainsArePresentAndIndependent() {
    var domains = java.util.Map.of("receiving", "ReceivingLimit", "stock", "BatchConversion",
        "title", "TitleShareBalance");
    String root = "com.appnexus.bulkwarehousing.domain.";
    domains.forEach((feature, type) -> {
      assertTrue(classes.contain(root + feature + "." + type));
      var otherDomains = domains.keySet().stream().filter(other -> !other.equals(feature))
          .map(other -> root + other + "..").toArray(String[]::new);
      noClasses().that().resideInAPackage(root + feature + "..")
          .should().dependOnClassesThat().resideInAnyPackage(otherDomains).check(classes);
    });
    noClasses().that().resideInAPackage(root + ".")
        .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.bulkwarehousing.api..",
            "com.appnexus.bulkwarehousing.bootstrap..", "com.appnexus.contracts..").check(classes);
  }

  /** 跨服务扫描实际领域及核心代码，禁止技术框架和外层适配器依赖。 */
  @Test void domainAndCoreRemainIndependentOfTechnology() {
    noClasses().that().resideInAnyPackage("..domain..", "..core..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "org.mybatis..", "org.apache.ibatis..",
            "java.sql..", "javax.sql..", "jakarta.servlet..", "org.flowable..",
            "io.lettuce..", "com.fasterxml.jackson..", "tools.jackson..",
            "..adapter..", "..adapters..", "..application..")
        .check(classes);
  }

  /** 应用输出端口与编码用例不能依赖技术实现，避免把 JDBC 包装成业务层接口后继续直接调用。 */
  @Test void migratedUseCasesAndPortsDependInwards() {
    noClasses().that().resideInAnyPackage(
            "com.appnexus.platform.businessobject.internal.application..", "com.appnexus.platform.businessobject.port..", "com.appnexus.masterdata.application..",
            "com.appnexus.workflow.application..", "com.appnexus.printing.application..")
        .or().haveFullyQualifiedName("com.appnexus.platform.businessobject.CodingManagement")
        .or().haveFullyQualifiedName("com.appnexus.platform.businessobject.BusinessObjectUseCases")
        .or().haveFullyQualifiedName("com.appnexus.masterdata.application.masterdata.MasterDataService")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "java.sql..", "javax.sql..",
            "com.fasterxml.jackson..", "tools.jackson..", "..adapter..", "..adapters..")
        .check(classes);
  }

  /** 明确验证已交付边界存在，防止删除或改包后规则因不再命中而失去约束。 */
  @Test void migratedBoundariesArePresentInProductionArtifact() {
    for (String name : new String[] {
        "com.appnexus.platform.businessobject.BusinessObjectManagement",
        "com.appnexus.platform.businessobject.CodingManagement",
        "com.appnexus.platform.businessobject.internal.application.configuration.BusinessObjectService",
        "com.appnexus.platform.businessobject.internal.application.execution.ObjectExecutionService",
        "com.appnexus.platform.businessobject.internal.application.execution.MappedObjectRuntime",
        "com.appnexus.platform.persistence.businessobject.MappedObjectRuntimeFactory",
        "com.appnexus.platform.businessobject.internal.application.execution.StandardCapabilityDrivers",
        "com.appnexus.workflow.adapter.flowable.ObjectWorkflowAdapter",
        "com.appnexus.workflow.application.ObjectApprovalService",
        "com.appnexus.platform.businessobject.port.ConfigurationStore",
        "com.appnexus.platform.businessobject.internal.domain.configuration.ConfigurationRules",
        "com.appnexus.platform.persistence.businessobject.configuration.MyBatisConfigurationStore",
        "com.appnexus.platform.businessobject.internal.application.coding.CodingService",
        "com.appnexus.platform.businessobject.internal.domain.coding.CodingRules",
        "com.appnexus.platform.businessobject.port.CodingStore",
        "com.appnexus.platform.persistence.businessobject.coding.MyBatisCodingStore",
        "com.appnexus.masterdata.domain.product.ProductLifecycle",
        "com.appnexus.masterdata.domain.product.ProductFilterRules",
        "com.appnexus.masterdata.domain.product.ProductCompletenessRules",
        "com.appnexus.masterdata.application.product.ProductListQueries",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductListQueries",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductCompletenessFacts",
        "com.appnexus.masterdata.domain.product.AttributeRulePolicy",
        "com.appnexus.masterdata.application.product.ProductCatalogQueryService",
        "com.appnexus.masterdata.application.product.ProductCatalogQueries",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductCatalogQueries",
        "com.appnexus.masterdata.application.product.ProductPreviewService",
        "com.appnexus.masterdata.application.product.ProductPreviewPort",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductAttributes",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductHistory",
        "com.appnexus.masterdata.application.product.ProductAttributePreparation",
        "com.appnexus.masterdata.application.product.ProductStructureService",
        "com.appnexus.masterdata.adapter.persistence.MyBatisProductSnapshot",
        "com.appnexus.workflow.application.TaskReassignmentService",
        "com.appnexus.printing.application.TemplateEditingService",
        "com.appnexus.masterdata.application.product.ProductLifecycleService",
        "com.appnexus.masterdata.application.product.ProductMainService",
        "com.appnexus.masterdata.application.product.ProductMainPort",
        "com.appnexus.masterdata.api.product.ProductMainData",
        "com.appnexus.masterdata.domain.product.ProductMainRules",
        "com.appnexus.masterdata.application.product.ProductLifecyclePort",
        "com.appnexus.workflow.application.TaskCompletionService",
        "com.appnexus.workflow.application.TaskCompletionPort",
        "com.appnexus.printing.application.TemplatePublicationService",
        "com.appnexus.printing.application.TemplatePublicationPort",
        "com.appnexus.masterdata.application.masterdata.MasterDataService",
        "com.appnexus.masterdata.domain.MasterDataRules",
        "com.appnexus.masterdata.application.masterdata.MasterDataRepository",
        "com.appnexus.masterdata.application.product.ProductRepository",
        "com.appnexus.masterdata.application.product.ProductUnitConversionService",
        "com.appnexus.masterdata.application.product.UnitConversionRepository",
        "com.appnexus.masterdata.domain.product.UnitConversionRules",
        "com.appnexus.workflow.application.WorkflowModelValidation",
        "com.appnexus.printing.core.PrintJobLifecycle",
        "com.appnexus.printing.application.PrintReceiptService",
        "com.appnexus.printing.application.PrintReceiptStore"
    }) assertTrue(classes.contain(name), () -> "生产产物缺少已迁移边界：" + name);
  }

  /** 公开协议与输入端口不得反向引用服务内部实现或技术框架。 */
  @Test void publicContractsRemainIndependentOfImplementations() {
    noClasses().that().resideInAnyPackage("com.appnexus.contracts..", "com.appnexus.platform.businessobject", "com.appnexus.platform.businessobject.port..", "com.appnexus.masterdata.api..")
        .and().doNotHaveFullyQualifiedName("com.appnexus.platform.businessobject.BusinessObjectUseCases")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..adapter..", "..adapters..", "..internal..", "..application..",
            "org.springframework..", "org.apache.ibatis..", "java.sql..", "org.flowable..")
        .check(classes);
  }

  /** 平台授权桥接必须通过公开租户端口，单位换算 HTTP 入口只依赖公开输入契约。 */
  @Test void migratedPlatformAndProductEntrypointsAvoidStorageTechnology() {
    noClasses().that().haveFullyQualifiedName("com.appnexus.platform.security.BusinessObjectPlatformBridge")
        .or().haveFullyQualifiedName("com.appnexus.masterdata.adapter.web.product.ProductUnitConversionController")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework.jdbc..", "com.appnexus.platform.persistence..",
            "com.appnexus.masterdata.application..", "com.appnexus.masterdata.adapter.persistence..")
        .check(classes);
  }

  /** 已迁移的 HTTP 与编码桥接必须通过公开输入端口，不直接引用配置用例实现或存储。 */
  @Test void configurationEntrypointsUsePublicInputPort() {
    noClasses().that().haveFullyQualifiedName("com.appnexus.platform.managementapi.businessobject.BusinessObjectController")
        .or().haveFullyQualifiedName("com.appnexus.platform.appapi.businessobject.CodingController")
        .or().haveFullyQualifiedName("com.appnexus.platform.businessobject.internal.application.access.ExistingCodingObjectAccess")
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.appnexus.platform.businessobject.internal.application.configuration..",
            "com.appnexus.platform.persistence.businessobject..", "org.springframework.jdbc..")
        .check(classes);
    noClasses().that().resideInAPackage("com.appnexus.platform.managementapi.businessobject..")
        .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.platform.businessobject.internal.application..", "com.appnexus.platform.businessobject.port..")
        .check(classes);
  }
  /** 平台对象上下文的内部实现只能由同上下文及无框架装配入口访问。 */
  @Test void businessObjectInternalsAreNotImportedByOtherModules() {
    noClasses().that().resideOutsideOfPackage("com.appnexus.platform.businessobject..")
        .should().dependOnClassesThat().resideInAPackage("com.appnexus.platform.businessobject.internal..")
        .check(classes);
    noClasses().that().resideInAnyPackage("com.appnexus.platform.managementapi.businessobject..", "com.appnexus.platform.appapi.businessobject..")
        .should().dependOnClassesThat().resideInAnyPackage("com.appnexus.platform.persistence..", "org.springframework.jdbc..")
        .check(classes);
  }

}
