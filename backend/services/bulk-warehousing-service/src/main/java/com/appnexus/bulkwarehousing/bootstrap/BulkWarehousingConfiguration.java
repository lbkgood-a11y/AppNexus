package com.appnexus.bulkwarehousing.bootstrap;

import com.appnexus.bulkwarehousing.api.inboundexecution.InboundSiteCommands;
import com.appnexus.bulkwarehousing.application.inboundexecution.InboundSiteCommandService;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.DirectoryInboundSiteCommands;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundSiteCommandController;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundSiteTelemetry;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReadInboundSiteTelemetryService;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.DirectoryInboundSiteTelemetry;

import com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.CommodityTemplateDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.CommodityTemplateMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisCommodityTemplateStore;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.CommodityTemplateController;
import com.appnexus.bulkwarehousing.application.inboundexecution.CommodityTemplateService;

import com.appnexus.bulkwarehousing.api.inboundexecution.OpenInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.OpenInboundReceiptService;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.CreateInboundTaskService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundTaskRegistrationMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundTaskRegistrationStore;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundTaskRegistrationController;

import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundDocumentsUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReadInboundDocumentsService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundDocumentMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundDocumentQueries;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundDocumentQueryController;
import com.appnexus.contracts.data.TransactionalBusinessEvents;
import com.appnexus.bulkwarehousing.application.inboundexecution.ApplyInboundBatchQualityService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundBatchQualityMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundBatchQualityProjection;
import com.appnexus.bulkwarehousing.api.inboundexecution.GroundWeighingUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.GroundWeighingService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.GroundWeighingMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisGroundWeighingRepository;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.GroundWeighingController;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.InboundTaskDeclaration;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.AttachmentSteelInspectionEvidenceAdapter;
import com.appnexus.bulkwarehousing.api.inboundexecution.InspectInboundSteelUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.InspectInboundSteelService;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.bulkwarehousing.api.inboundexecution.ConfirmSteelMeasurementUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.ConfirmSteelMeasurementService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.SteelMeasurementMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisSteelMeasurementRepository;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateSteelReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.CreateSteelReceiptDraftService;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchDetailUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReadInboundWorkbenchDetailService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundWorkbenchDetailMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundWorkbenchDetailQueries;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.InboundReceiptOrderDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundReceiptOrderMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundReceiptOrderRepository;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundReceiptOrderController;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.CreateBulkReceiptDraftService;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisBulkReceiptPostingRepository;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.ReceiptPostingMapper;
import com.appnexus.bulkwarehousing.api.inboundexecution.PostInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.PostInboundReceiptService;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReverseInboundReceiptUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReverseInboundReceiptService;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.InboundMeteringOrderDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundMeteringOrderMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundMeteringOrderRepository;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundMeteringOrderController;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterExternalMeasurementUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.RegisterExternalMeasurementService;
import com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.InboundQualityOrderDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundQualityOrderMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundQualityOrderRepository;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundQualityOrderController;
import com.appnexus.bulkwarehousing.api.inboundexecution.RegisterInboundSampleUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.RegisterInboundSampleService;
import com.appnexus.bulkwarehousing.application.inboundexecution.DecideInboundQualityService;
import com.appnexus.bulkwarehousing.application.inboundexecution.InboundQualityOrderRepository;
import com.appnexus.bulkwarehousing.api.inboundexecution.DecideInboundQualityUseCase;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.InboundWorkbenchMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.inboundexecution.MyBatisInboundWorkbenchQueries;
import com.appnexus.bulkwarehousing.adapter.web.inboundexecution.InboundWorkbenchController;
import com.appnexus.bulkwarehousing.api.inboundexecution.ReadInboundWorkbenchUseCase;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReadInboundWorkbenchService;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.bulkwarehousing.adapter.integration.MasterDataInboundReceiptReferences;
import com.appnexus.bulkwarehousing.adapter.integration.InboundReceivingDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.InboundReceivingMapper;
import com.appnexus.bulkwarehousing.adapter.web.InboundReceivingController;
import com.appnexus.bulkwarehousing.api.InboundReceiving;
import com.appnexus.bulkwarehousing.api.InboundOperations;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisInboundReceivingStore;
import com.appnexus.bulkwarehousing.application.InboundReceivingService;
import com.appnexus.contracts.businessobject.BusinessObjects;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.application.ResolveInboundReceiptReferences;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.product.ProductStructureManagement;
import com.appnexus.masterdata.api.product.UnitConversions;
import com.appnexus.bulkwarehousing.adapter.web.WarehouseQueryController;
import com.appnexus.masterdata.api.product.ProductCatalogManagement;
import com.appnexus.bulkwarehousing.api.WarehouseBalances;
import com.appnexus.bulkwarehousing.application.WarehouseBalanceService;
import com.appnexus.bulkwarehousing.adapter.integration.WarehouseStockBalanceDeclaration;
import com.appnexus.bulkwarehousing.adapter.persistence.WarehouseBalanceMapper;
import com.appnexus.bulkwarehousing.adapter.persistence.MyBatisWarehouseBalanceStore;
import com.appnexus.masterdata.api.product.ProductManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 大宗仓储的进程内装配，复用同一组合应用中的主数据服务。 */
@Configuration(proxyBeanMethods = false)
public class BulkWarehousingConfiguration {
  /** @param access 统一权限 @param mapper 固定分岗查询 @param gate 公共事务许可 @param manager 同库事务 @return 独立单据读用例 */
  @Bean ReadInboundDocumentsUseCase readInboundDocuments(AccessPlanning access,InboundDocumentMapper mapper,
      ScopedWriteGate gate,PlatformTransactionManager manager) {
    return new ReadInboundDocumentsService(access,new MyBatisInboundDocumentQueries(mapper,gate,manager));
  }

  /** @param documents 已授权单据读取 @return 第一方分岗列表及详情 */
  @Bean InboundDocumentQueryController inboundDocumentQueryController(ReadInboundDocumentsUseCase documents) {
    return new InboundDocumentQueryController(documents);
  }
  /**
   * @param mapper 地磅和监卸 SQL
   * @param manager 主库事务
   * @param gate 公共操作许可
   * @param access 统一操作与数据计划
   * @return 草稿毛重、监卸和皮重命令
   */
  @Bean public GroundWeighingService groundWeighing(GroundWeighingMapper mapper,
      PlatformTransactionManager manager, ScopedWriteGate gate, AccessPlanning access) {
    return new GroundWeighingService(access,new MyBatisGroundWeighingRepository(mapper,gate,manager));
  }

  /** @param weighing 受控地磅与监卸业务 @return 独立岗位接口 */
  @Bean public GroundWeighingController groundWeighingController(GroundWeighingUseCase weighing) {
    return new GroundWeighingController(weighing);
  }
  /** @param operations 固定调度授权 @param gate 统一事务许可 @param mapper 定向质量库存 SQL @return 同库 Outbox 消费者 */
  @Bean public ApplyInboundBatchQualityService applyInboundBatchQuality(BusinessObjects.ScheduledOperations operations,
      ScopedWriteGate gate, InboundBatchQualityMapper mapper) {
    return new ApplyInboundBatchQualityService(operations, gate, new MyBatisInboundBatchQualityProjection(mapper));
  }
  /** @return 入库单唯一声明 */
  @Bean public InboundReceiptOrderDeclaration inboundReceiptOrderDeclaration() {
    return new InboundReceiptOrderDeclaration();
  }

  /**
   * @param mapper 入库草稿固定 SQL
   * @param manager 组合应用同一事务
   * @param access 统一权限计划
   * @param gate 公共事务许可
   * @param references 仓储授权引用与单位换算用例
   * @param source 可信千克单位检查
   * @return 已装配的入库草稿用例
   */
  @Bean public CreateBulkReceiptDraftService createBulkReceiptDraft(InboundReceiptOrderMapper mapper,
      PlatformTransactionManager manager, AccessPlanning access, ScopedWriteGate gate,
      InboundReceiptReferences references, MasterDataInboundReceiptReferences source) {
    return new CreateBulkReceiptDraftService(access, new MyBatisInboundReceiptOrderRepository(mapper, gate, manager),
        references, source);
  }

  /**
   * @param mapper 固定过账 SQL
   * @param manager 组合应用共用事务管理器
   * @param gate MyBatis 事务许可
   * @return 过账、冲红和幂等回执共用的仓储
   */
  @Bean public MyBatisBulkReceiptPostingRepository receiptPostingRepository(ReceiptPostingMapper mapper,
      PlatformTransactionManager manager, ScopedWriteGate gate) {
    return new MyBatisBulkReceiptPostingRepository(mapper, gate, manager);
  }

  /** @param access 公共授权 @param repository 过账仓储 @param source 主数据核验 @return 合格散货过账用例 */
  @Bean public PostInboundReceiptService postInboundReceipt(AccessPlanning access,
      MyBatisBulkReceiptPostingRepository repository, MasterDataInboundReceiptReferences source,
      TransactionalBusinessEvents events) {
    return new PostInboundReceiptService(access, repository, repository, source, events);
  }

  /** @param access 公共授权 @param repository 逆向库存仓储 @return 受控冲红用例 */
  @Bean public ReverseInboundReceiptService reverseInboundReceipt(AccessPlanning access,
      MyBatisBulkReceiptPostingRepository repository) {
    return new ReverseInboundReceiptService(access, repository, repository);
  }

  /** @param mapper 草稿 SQL @param manager 主库事务 @param access 公共授权 @param gate 公共许可 @param references 引用 @param source 千克验证 @return 钢铁草稿 */
  @Bean public CreateSteelReceiptDraftService createSteelReceiptDraft(InboundReceiptOrderMapper mapper,
      PlatformTransactionManager manager,AccessPlanning access,ScopedWriteGate gate,
      InboundReceiptReferences references,MasterDataInboundReceiptReferences source) {
    return new CreateSteelReceiptDraftService(access,new MyBatisInboundReceiptOrderRepository(mapper,gate,manager),references,source);
  }

  /** @param drafts 散货草稿 @param posting 过账 @param reversal 冲红 @param steelDrafts 钢铁草稿 @return 独立入库命令入口 */
  @Bean OpenInboundReceiptUseCase openInboundReceipt(AccessPlanning access,InboundReceiptOrderMapper mapper,
      ScopedWriteGate gate,PlatformTransactionManager manager) {
    return new OpenInboundReceiptService(access,new MyBatisInboundReceiptOrderRepository(mapper,gate,manager));
  }

  @Bean public InboundReceiptOrderController inboundReceiptOrderController(CreateBulkReceiptDraftUseCase drafts,
      PostInboundReceiptUseCase posting, ReverseInboundReceiptUseCase reversal,CreateSteelReceiptDraftUseCase steelDrafts,
      OpenInboundReceiptUseCase opening) {
    return new InboundReceiptOrderController(drafts, posting, reversal,steelDrafts,opening);
  }
  @Bean InboundSiteCommandService inboundSiteCommands(InboundWorkbenchDetailMapper mapper,
      PlatformTransactionManager manager,ScopedWriteGate gate,AccessPlanning access,org.springframework.core.env.Environment environment) {
    var channel=new DirectoryInboundSiteCommands(environment.getProperty("app.inbound.site.command-directory",""),
        environment.getProperty("app.inbound.site.simulated",Boolean.class,false));
    return new InboundSiteCommandService(new MyBatisInboundWorkbenchDetailQueries(mapper,gate,manager),access,channel,
        java.time.Clock.systemUTC(),java.time.Duration.ofSeconds(8));
  }
  @Bean InboundSiteCommandController inboundSiteCommandController(InboundSiteCommands commands) {
    return new InboundSiteCommandController(commands);
  }

  /** @return 计量单唯一对象声明 */
  @Bean public InboundMeteringOrderDeclaration inboundMeteringOrderDeclaration() {
    return new InboundMeteringOrderDeclaration();
  }

  /**
   * @param mapper 外部计量固定 SQL
   * @param manager 组合应用事务
   * @param access 统一权限计划
   * @param gate 公共事务许可
   * @return 外部计量确认用例
   */
  @Bean public RegisterExternalMeasurementService registerExternalMeasurement(InboundMeteringOrderMapper mapper,
      PlatformTransactionManager manager, AccessPlanning access, ScopedWriteGate gate) {
    return new RegisterExternalMeasurementService(access, new MyBatisInboundMeteringOrderRepository(mapper, gate, manager));
  }

  /** @param access 统一授权 @param mapper 双计量 SQL @param tickets 票据 @param gate 公共许可 @param manager 主库事务 @return 钢铁计量 */
  @Bean public ConfirmSteelMeasurementService confirmSteelMeasurement(AccessPlanning access,SteelMeasurementMapper mapper,
      GroundWeighingMapper tickets,ScopedWriteGate gate,PlatformTransactionManager manager) {
    return new ConfirmSteelMeasurementService(access,new MyBatisSteelMeasurementRepository(mapper,tickets,gate,manager));
  }

  /** @param useCase 外部凭证登记 @param steel 钢铁双计量 @return 独立计量入口 */
  @Bean public InboundMeteringOrderController inboundMeteringOrderController(RegisterExternalMeasurementUseCase useCase,
      ConfirmSteelMeasurementUseCase steel) {
    return new InboundMeteringOrderController(useCase,steel);
  }
  /** @return 入库质检单的唯一对象声明 */
  @Bean public InboundQualityOrderDeclaration inboundQualityOrderDeclaration() {
    return new InboundQualityOrderDeclaration();
  }

  /**
   * @param mapper 质检登记与判定的固定 SQL
   * @param manager 组合应用事务管理器
   * @param gate 绑定事务的公共操作许可
   * @return 共享锁、存储与审计的质检仓储
   */
  @Bean public InboundQualityOrderRepository inboundQualityOrderRepository(InboundQualityOrderMapper mapper,
      PlatformTransactionManager manager, ScopedWriteGate gate) {
    return new MyBatisInboundQualityOrderRepository(mapper, gate, manager);
  }

  /** @param access 统一权限计划 @param repository 质检仓储 @return 采样与复检用例 */
  @Bean public RegisterInboundSampleService registerInboundSample(AccessPlanning access,
      InboundQualityOrderRepository repository) {
    return new RegisterInboundSampleService(access, repository);
  }

  /** @param access 统一权限计划 @param repository 质检仓储 @return 实验室判定用例 */
  @Bean public DecideInboundQualityService decideInboundQuality(AccessPlanning access,
      InboundQualityOrderRepository repository, TransactionalBusinessEvents events) {
    return new DecideInboundQualityService(access, repository, events);
  }

  /** @param access 统一权限计划 @param repository 质检仓储 @param attachments 可用附件公开契约 @return 钢铁核验用例 */
  @Bean public InspectInboundSteelService inspectInboundSteel(AccessPlanning access,
      InboundQualityOrderRepository repository, AttachmentRecords attachments) {
    return new InspectInboundSteelService(access, repository, new AttachmentSteelInspectionEvidenceAdapter(attachments));
  }

  /** @param samples 采样用例 @param decisions 判定用例 @param steel 实物和质保书核验 @return 独立质检命令入口 */
  @Bean public InboundQualityOrderController inboundQualityOrderController(RegisterInboundSampleUseCase samples,
      DecideInboundQualityUseCase decisions, InspectInboundSteelUseCase steel) {
    return new InboundQualityOrderController(samples, decisions, steel);
  }
  /** @return 入库作业及队列操作的唯一声明 */
  @Bean CreateInboundTaskUseCase createInboundTask(AccessPlanning access, InboundTaskRegistrationMapper mapper,
      com.appnexus.platform.authorization.FieldProtection fields,ScopedWriteGate gate,PlatformTransactionManager manager) {
    return new CreateInboundTaskService(access,fields,new MyBatisInboundTaskRegistrationStore(mapper,gate,manager));
  }

  @Bean InboundTaskRegistrationController inboundTaskRegistrationController(CreateInboundTaskUseCase useCase) {
    return new InboundTaskRegistrationController(useCase);
  }

  @Bean CommodityTemplateService commodityTemplates(AccessPlanning access,CommodityTemplateMapper mapper,
      ScopedWriteGate gate,PlatformTransactionManager manager,MasterDataInboundReceiptReferences references) {
    return new CommodityTemplateService(access,new MyBatisCommodityTemplateStore(mapper,gate,manager),references);
  }

  @Bean CommodityTemplateDeclaration commodityTemplateDeclaration() {return new CommodityTemplateDeclaration();}
  @Bean CommodityTemplateController commodityTemplateController(CommodityTemplates templates) {
    return new CommodityTemplateController(templates);
  }

  /** @param site 原现场权限与作业状态入口 @param tenants 权威成员目录 @return 入库作业唯一声明 */
  @Bean public InboundTaskDeclaration inboundTaskDeclaration(
      org.springframework.beans.factory.ObjectProvider<InboundSiteCommandService> site,
      org.springframework.beans.factory.ObjectProvider<com.appnexus.platform.tenant.TenantAccess> tenants) {
    return new InboundTaskDeclaration(new com.appnexus.bulkwarehousing.adapter.integration.inboundexecution.InboundTaskPrintRuntime(
        site::getIfAvailable,tenants::getIfAvailable));
  }

  /**
   * @param mapper 定向队列 SQL
   * @param manager 组合应用事务管理器
   * @param access 统一操作及范围计划
   * @param gate 查询事务的公共许可
   * @return 工作台队列读取用例与实际数据范围执行器
   */
  @Bean public ReadInboundWorkbenchService inboundWorkbench(InboundWorkbenchMapper mapper,
      PlatformTransactionManager manager, AccessPlanning access, ScopedWriteGate gate) {
    return new ReadInboundWorkbenchService(access, new MyBatisInboundWorkbenchQueries(mapper, gate, manager));
  }

  /** @param access 统一操作计划 @param fields 字段保护 @param mapper 固定查询 @param gate 公共许可 @param manager 主库事务 @return 详情聚合 */
  @Bean public ReadInboundWorkbenchDetailService inboundWorkbenchDetails(AccessPlanning access,
      com.appnexus.platform.authorization.FieldProtection fields,InboundWorkbenchDetailMapper mapper,
      ScopedWriteGate gate,PlatformTransactionManager manager) {
    return new ReadInboundWorkbenchDetailService(access,fields,new MyBatisInboundWorkbenchDetailQueries(mapper,gate,manager));
  }

  /** @param useCase 授权队列 @param detail 授权详情 @return 第一方工作台只读入口 */
  @Bean public InboundWorkbenchController inboundWorkbenchController(ReadInboundWorkbenchUseCase useCase,
      ReadInboundWorkbenchDetailUseCase detail,ReadInboundSiteTelemetry site) {
    return new InboundWorkbenchController(useCase,detail,site);
  }
  @Bean ReadInboundSiteTelemetry inboundSiteTelemetry(ReadInboundWorkbenchDetailUseCase detail,AccessPlanning access,
      com.appnexus.platform.authorization.FieldProtection fields,org.springframework.core.env.Environment environment) {
    var source=new DirectoryInboundSiteTelemetry(environment.getProperty("app.inbound.site.directory",""),
        environment.getProperty("app.inbound.site.simulated",Boolean.class,false));
    return new ReadInboundSiteTelemetryService(detail,access,fields,source,java.time.Clock.systemUTC());
  }
  /** @param balances 授权仓储读取用例 @return 入仓通知与库存总览查询入口 */
  @Bean public WarehouseQueryController warehouseQueryController(WarehouseBalances balances) {
    return new WarehouseQueryController(balances);
  }

  /** @return 在库货权余额资源唯一声明 */
  @Bean public BusinessObjects.Provider warehouseStockBalanceDeclaration() { return new WarehouseStockBalanceDeclaration(); }

  /** @param mapper 仓储查询 SQL @param manager 组合应用的事务管理器
   * @param names 公共主数据名称读取
   * @param fields 平台统一字段保护 @param access 统一权限 @param operations 公共操作许可 @return 入仓通知与库存总览查询 */
  @Bean public WarehouseBalanceService warehouseBalances(WarehouseBalanceMapper mapper,PlatformTransactionManager manager,
      com.appnexus.masterdata.api.masterdata.MasterDataReferenceNames names,
      AccessPlanning access,com.appnexus.platform.authorization.ScopedWriteGate operations,
      com.appnexus.platform.authorization.FieldProtection fields) {
    return new WarehouseBalanceService(new MyBatisWarehouseBalanceStore(mapper,manager),names::load,access,operations,fields);
  }

  /** @param receiving 受控入仓用例 @return 第一方命令控制器 */
  @Bean public InboundReceivingController inboundReceivingController(InboundReceiving receiving) {
    return new InboundReceivingController(receiving);
  }

  /** @return 入仓履约唯一对象声明 */
  @Bean public BusinessObjects.Provider inboundReceivingDeclaration() {
    return new InboundReceivingDeclaration(InboundOperations.OBJECT,"入仓通知单","Inbound notice");
  }

  /**
   * @param mapper 仓储固定 SQL
   * @param manager 同一数据源事务管理器
   * @param source 主数据集成适配器
   * @param access 平台统一权限计划
   * @param fields 平台统一司机字段保护
   * @return 入仓通知签发用例
   */
  @Bean public InboundReceivingService inboundReceiving(InboundReceivingMapper mapper, PlatformTransactionManager manager,
      MasterDataInboundReceiptReferences source, AccessPlanning access,
      com.appnexus.platform.authorization.ScopedWriteGate writes,com.appnexus.platform.authorization.FieldProtection fields) {
    return new InboundReceivingService(new MyBatisInboundReceivingStore(mapper,manager),source,access,writes,fields);
  }

  /** @return 受治理的主数据出站适配器 */
  @Bean public MasterDataInboundReceiptReferences inboundMasterData(MasterDataManagement masterData,
      ProductStructureManagement products,UnitConversions conversions,ProductCatalogManagement catalog,ProductManagement productRead,
      com.appnexus.masterdata.api.masterdata.MasterDataReferenceNames names) {
    return new MasterDataInboundReceiptReferences(masterData,products,conversions,catalog,productRead,names);
  }

  /**
   * @param source 主数据集成适配器 主数据授权查询入口
   * @param transactions 组合应用共用的数据库事务管理器
   * @return 事务内核验引用的只读入口，调用方已开启事务时加入其事务
   */
  @Bean
  public InboundReceiptReferences inboundReceiptReferences(MasterDataInboundReceiptReferences source,
      PlatformTransactionManager transactions) {
    var useCase = new ResolveInboundReceiptReferences(source);
    var read = new TransactionTemplate(transactions);
    read.setReadOnly(true);
    return (context, request) -> read.execute(status -> useCase.resolve(context, request));
  }
}
