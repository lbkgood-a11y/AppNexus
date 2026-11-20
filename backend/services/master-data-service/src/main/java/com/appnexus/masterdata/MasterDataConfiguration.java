package com.appnexus.masterdata;

import com.appnexus.platform.authorization.AccessAuthorization;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

/** 主数据由组合应用显式装配；不依赖审批开关，不创建模拟档案或登录账号。 */
@Configuration(proxyBeanMethods=false)
@ComponentScan(basePackageClasses=MasterDataConfiguration.class)
public class MasterDataConfiguration {
  /** @param mapper 固定单位换算Mapper，与事务共用连接 @param access 可信功能权限端口 @param manager 同数据源 REQUIRED 事务管理器 @return 商品单位换算纯应用用例，不提供技术兼容构造入口 */
  @Bean public static com.appnexus.masterdata.application.product.ProductUnitConversionService conversions(
      com.appnexus.masterdata.adapter.jdbc.UnitConversionMapper mapper,AccessAuthorization access,PlatformTransactionManager manager){
    return new com.appnexus.masterdata.application.product.ProductUnitConversionService(
        new com.appnexus.masterdata.adapter.jdbc.MyBatisUnitConversionStore(mapper),access,
        new com.appnexus.masterdata.adapter.jdbc.JdbcRecordTransaction(manager));
  }
  /** 商品父记录端口由组合装配提供；与商品明细沿用同一数据源及事务管理器。 */
  @Bean public com.appnexus.masterdata.application.product.ProductRecords productRecords(com.appnexus.masterdata.adapter.jdbc.ProductRecordMapper mapper){return new com.appnexus.masterdata.adapter.jdbc.MyBatisProductRecords(mapper);}

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
  @Bean public MasterDataService masterDataService(com.appnexus.masterdata.adapter.jdbc.MasterRecordMapper records,MasterReadMapper reads,GeographyMapper geography,StorageHierarchyMapper storage,PartnerDetailMapper details,AccessAuthorization access,PlatformTransactionManager manager,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries,PartnerDataAccess partner){return create(records,reads,geography,storage,details,access,manager,dictionaries,partner);}
  /**
   * 显式组合主数据输入用例与技术输出，独立数据库测试沿用同一装配。
   * @param jdbc UTC 数据源，所有适配器必须共用
   * @param access 公开可信权限端口
   * @param manager 同数据源事务管理器，父记录和明细不得独立提交
   * @param dictionaries 公开字典与引用契约
   * @param partner 客商数据权限执行器；null 时不开放客商访问
   * @return 不依赖技术实现的主数据应用用例
   */
  public static MasterDataService create(com.appnexus.masterdata.adapter.jdbc.MasterRecordMapper records,MasterReadMapper reads,GeographyMapper geography,StorageHierarchyMapper storage,PartnerDetailMapper details,AccessAuthorization access,PlatformTransactionManager manager,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries,PartnerDataAccess partner){
    return new MasterDataService(access,dictionaries,new com.appnexus.masterdata.adapter.jdbc.MyBatisMasterDataRecords(records),new MyBatisMasterDataRelations(reads,geography,storage,details,dictionaries,partner),new com.appnexus.masterdata.adapter.jdbc.JdbcRecordTransaction(manager),partner);
  }
}
