package com.appnexus.masterdata.bootstrap;
import com.appnexus.masterdata.adapter.persistence.masterdata.PartnerDataAccess;
import com.appnexus.masterdata.adapter.persistence.masterdata.MyBatisMasterDataRelations;
import com.appnexus.masterdata.adapter.persistence.masterdata.GeographyDefaults;
import com.appnexus.masterdata.application.masterdata.MasterDataService;
import com.appnexus.masterdata.adapter.persistence.StorageHierarchyMapper;
import com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper;
import com.appnexus.masterdata.adapter.persistence.MasterReadMapper;
import com.appnexus.masterdata.adapter.persistence.GeographyMapper;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.authorization.DataAccessCatalog;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

/** 主数据由组合应用显式装配；不依赖审批开关，不创建模拟档案或登录账号。 */
@Configuration(proxyBeanMethods=false)
@ComponentScan("com.appnexus.masterdata")
public class MasterDataConfiguration {
  /** @param mapper 固定单位换算Mapper，与事务共用连接 @param access 可信功能权限端口 @param manager 同数据源 REQUIRED 事务管理器 @param writes 公共事务门禁 @return 商品单位换算纯应用用例，不提供技术兼容构造入口 */
  @Bean public static com.appnexus.masterdata.application.product.ProductUnitConversionService conversions(
      com.appnexus.masterdata.adapter.persistence.UnitConversionMapper mapper,AccessAuthorization access,PlatformTransactionManager manager,
      com.appnexus.platform.authorization.ScopedWriteGate writes, com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper statusHistory){
    return new com.appnexus.masterdata.application.product.ProductUnitConversionService(
        new com.appnexus.masterdata.adapter.persistence.MyBatisUnitConversionRepository(mapper,statusHistory),access,
        new com.appnexus.masterdata.adapter.transaction.SpringRecordTransaction(manager),writes);
  }
  /** 商品父记录端口由组合装配提供；与商品明细沿用同一数据源及事务管理器。 */
  @Bean public com.appnexus.masterdata.application.product.ProductRepository productRecords(com.appnexus.masterdata.adapter.persistence.ProductRecordMapper mapper){return new com.appnexus.masterdata.adapter.persistence.MyBatisProductRepository(mapper);}

  /** @return 沿用主档准入与同一数据源读事务的启停历史查询 */
  @Bean public com.appnexus.masterdata.application.masterdata.StatusHistoryQuery statusHistory(
      com.appnexus.masterdata.api.masterdata.MasterDataManagement records,
      AccessAuthorization access, PlatformTransactionManager manager,
      com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper mapper) {
    return new com.appnexus.masterdata.application.masterdata.StatusHistoryQuery(records, access,
        new com.appnexus.masterdata.adapter.transaction.SpringRecordTransaction(manager),
        new com.appnexus.masterdata.adapter.persistence.MyBatisStatusHistoryRepository(mapper));
  }

  /** 同步订阅开通事件，复用发布方事务；本模块缺省装配时不建立跨模块数据库访问。 */
  @Bean public GeographyProvisioning geographyProvisioning(GeographyMapper mapper){return new GeographyProvisioning(mapper);}

  /** 开通事务内写入当前版本的地理目录；普通查询无初始化副作用。 */
  public record GeographyProvisioning(GeographyMapper mapper) {
    /** @param event 可信平台开通事件，账号来自已授权开通执行器 */
    @org.springframework.context.event.EventListener
    public void provision(com.appnexus.platform.tenant.TenantProvisioned event){GeographyDefaults.seed(mapper,event.tenantId(),event.actorId());}
  }
  /**
   * @return 复用宿主事务、可信租户和公开授权端口的主数据执行器 */
  @Bean public MasterDataService masterDataService(com.appnexus.masterdata.adapter.persistence.MasterRecordMapper records,MasterReadMapper reads,GeographyMapper geography,StorageHierarchyMapper storage,PartnerDetailMapper details,AccessAuthorization access,PlatformTransactionManager manager,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries,PartnerDataAccess partner,FieldProtection fields,DataAccessCatalog catalog,com.appnexus.platform.authorization.AccessPlanning planning,com.appnexus.platform.authorization.RowAccessControl rowAccess,com.appnexus.platform.authorization.DataAccessReferences references,
      com.appnexus.platform.authorization.ScopedWriteGate writes, com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper statusHistory){
    return create(records,reads,geography,storage,details,access,manager,dictionaries,partner,fields,catalog,planning,rowAccess,references,writes,statusHistory);
  }
  /**
   * 组合主数据输入用例与技术输出，独立数据库测试沿用相同装配。
   * @param records 固定档案写映射
   * @param reads 固定档案查询及授权锁
   * @param geography 地理固定映射
   * @param storage 仓储层级固定映射
   * @param details 客商明细固定映射
   * @param access 实时功能授权
   * @param manager 同数据源事务管理器
   * @param dictionaries 字典与引用端口
   * @param partner 客商范围及字段执行器
   * @param fields 统一字段策略执行器
   * @param catalog 统一对象派生目录
   * @param planning 仓库实时权限计划
   * @param rowAccess 平台公共行范围编译
   * @param references 同租户启用组织的权威公开目录
   * @param writes 当前事务的仓库记录写入许可
   * @return 主数据应用用例
   */
  public static MasterDataService create(com.appnexus.masterdata.adapter.persistence.MasterRecordMapper records,
      MasterReadMapper reads,GeographyMapper geography,StorageHierarchyMapper storage,PartnerDetailMapper details,
      AccessAuthorization access,PlatformTransactionManager manager,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries,
      PartnerDataAccess partner,FieldProtection fields,DataAccessCatalog catalog,com.appnexus.platform.authorization.AccessPlanning planning,
      com.appnexus.platform.authorization.RowAccessControl rowAccess,com.appnexus.platform.authorization.DataAccessReferences references,
      com.appnexus.platform.authorization.ScopedWriteGate writes, com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper statusHistory){
    return new MasterDataService(access,dictionaries,new com.appnexus.masterdata.adapter.persistence.MyBatisMasterDataRepository(records,access,writes,statusHistory),
        new MyBatisMasterDataRelations(reads,geography,storage,details,dictionaries,partner,planning,rowAccess,references,writes),
        new com.appnexus.masterdata.adapter.transaction.SpringRecordTransaction(manager),partner,fields,catalog);
  }
  /** 商品图片纯用例装配；父授权、CAS、图片、历史与审计共用事务。 */
  @Bean public com.appnexus.masterdata.api.product.ProductImageManagement productImageManagement(AccessAuthorization access,PlatformTransactionManager manager,com.appnexus.masterdata.application.product.ProductAggregateAccess products,com.appnexus.masterdata.application.product.ProductImageRepository images,com.appnexus.masterdata.application.product.ProductImageContent content){return new com.appnexus.masterdata.application.product.ProductImageService(access,new com.appnexus.masterdata.adapter.transaction.SpringRecordTransaction(manager),products,images,content);}
  /** 商品模板纯应用用例装配；发布、退役和模板项仍在同一完整事务中执行。 */
  @Bean public com.appnexus.masterdata.api.product.ProductTemplateManagement productTemplateManagement(AccessAuthorization access,com.appnexus.masterdata.application.product.ProductTemplateTransaction transaction,com.appnexus.masterdata.application.product.ProductTemplateRepository repository,com.appnexus.platform.authorization.ScopedWriteGate writes){return new com.appnexus.masterdata.application.product.ProductTemplateService(access,transaction,repository,writes);}
  /** 属性纯应用用例装配，固定JSON解析和存储通过输出端口连接。 */
  @Bean public com.appnexus.masterdata.api.product.ProductAttributeManagement productAttributeManagement(AccessAuthorization access,com.appnexus.masterdata.application.product.ProductAttributeTransaction transaction,com.appnexus.masterdata.application.product.ProductAttributeRepository repository,com.appnexus.masterdata.application.product.AttributeRuleContent rules,com.appnexus.platform.authorization.ScopedWriteGate writes){return new com.appnexus.masterdata.application.product.ProductAttributeService(access,transaction,repository,rules,writes);}
  /** 商品目录纯应用用例装配，保留分类锚点锁、发布快照与原完整性拒绝范围。 */
  @Bean public com.appnexus.masterdata.api.product.ProductCatalogManagement productCatalogManagement(AccessAuthorization access,com.appnexus.masterdata.application.product.ProductCatalogTransaction transaction,com.appnexus.masterdata.application.product.ProductCatalogRepository repository,com.appnexus.platform.authorization.ScopedWriteGate writes){return new com.appnexus.masterdata.application.product.ProductCatalogService(access,transaction,repository,writes);}
  /** 商品规格、SKU及组合结构纯应用用例装配。 */
  @Bean public com.appnexus.masterdata.api.product.ProductStructureManagement productStructureManagement(AccessAuthorization access,com.appnexus.masterdata.application.product.ProductStructureTransaction transaction,com.appnexus.masterdata.application.product.ProductAggregateAccess products,com.appnexus.masterdata.application.product.ProductStructureRepository repository,com.appnexus.masterdata.application.product.AttributeRuleContent rules,com.appnexus.masterdata.application.product.ProductContent content){return new com.appnexus.masterdata.application.product.ProductStructureService(access,transaction,products,repository,rules,content);}
  /** 商品固定目录查询端口，所有引用验证沿用原SQL及锁。 */
  @Bean public com.appnexus.masterdata.application.product.ProductCatalogQueries productCatalogQueries(com.appnexus.masterdata.adapter.persistence.ProductCatalogReadMapper mapper){return new com.appnexus.masterdata.adapter.persistence.MyBatisProductCatalogQueries(mapper);}
  /** 商品列表保留原JSON过滤、记录范围及同条件COUNT。 */
  @Bean public com.appnexus.masterdata.application.product.ProductListQueries productListQueries(com.appnexus.masterdata.adapter.persistence.ProductListMapper mapper,com.appnexus.masterdata.adapter.persistence.ProductAccess access){return new com.appnexus.masterdata.adapter.persistence.MyBatisProductListQueries(mapper,new tools.jackson.databind.ObjectMapper(),access);}
  /** 商品主档完整用例装配，只有本装配连接技术输出与应用实现。 */
  @Bean public com.appnexus.masterdata.application.product.ProductService productService(AccessAuthorization access,com.appnexus.contracts.operation.OperationPreviewProof proof,com.appnexus.masterdata.application.product.ProductRepository records,com.appnexus.masterdata.application.product.ProductCommandRepository commands,com.appnexus.masterdata.application.product.ProductAggregateRepository aggregates,com.appnexus.masterdata.application.product.ProductListQueries list,com.appnexus.masterdata.application.product.ProductCatalogQueries catalog,com.appnexus.masterdata.application.product.ProductAuthorization authorization,com.appnexus.masterdata.application.product.ProductExecutionTransaction transaction,com.appnexus.masterdata.application.product.ProductContent content,com.appnexus.masterdata.application.product.AttributeRuleContent rules,com.appnexus.masterdata.application.product.ProductCompletenessService completeness){return new com.appnexus.masterdata.application.product.ProductService(access,proof,records,commands,aggregates,list,catalog,authorization,transaction,content,rules,completeness);}
  /** 商品完整性纯应用协调装配，存储只提供事实与状态写入。 */
  @Bean public com.appnexus.masterdata.application.product.ProductCompletenessService productCompleteness(com.appnexus.masterdata.application.product.ProductCompletenessFacts facts,com.appnexus.masterdata.application.product.ProductRepository records){return new com.appnexus.masterdata.application.product.ProductCompletenessService(facts,records);}
}
