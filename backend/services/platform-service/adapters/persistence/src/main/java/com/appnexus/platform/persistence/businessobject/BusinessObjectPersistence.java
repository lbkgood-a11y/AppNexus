package com.appnexus.platform.persistence.businessobject;

import com.appnexus.platform.businessobject.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.WorkflowDefinitionCatalog;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;

/** 对象能力的持久化装配入口；生产与数据库测试共用，返回值仅为公开用例。 */
public final class BusinessObjectPersistence {
  private BusinessObjectPersistence() {}
  /**
   * 组装真实配置存储及事务；不承担业务决策。
   * @param mapper 对象配置固定 Mapper，必须与 manager 共用数据源
   * @param manager REQUIRED 事务管理器，配置、审计及解析共同回滚
   * @param platform 可信主体和实时授权端口
   * @param workflows 延迟流程目录，关闭时允许 null
   * @param providers 唯一对象声明集合
   * @return 配置公开用例；默认未装配打印能力
   */
  public static BusinessObjectManagement create(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper mapper, PlatformTransactionManager manager,
      BusinessObjectPlatform platform, Supplier<WorkflowDefinitionCatalog> workflows, List<BusinessObjects.Provider> providers) {
    return create(mapper,manager,platform,workflows,providers,()->null);
  }
  /**
   * 组装配置用例与可选打印目录，打印能力关闭不影响直接业务操作。
   * @param mapper 与事务共用 SqlSession 的配置 Mapper
   * @param manager REQUIRED 事务管理器，异常整笔回滚
   * @param platform 可信主体及实时授权端口
   * @param workflows 延迟流程目录，关闭时允许 null
   * @param providers 唯一业务对象声明
   * @param printing 延迟打印公开能力，关闭时允许 null
   * @return 配置公开用例
   */
  public static BusinessObjectManagement create(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper mapper, PlatformTransactionManager manager,
      BusinessObjectPlatform platform, Supplier<WorkflowDefinitionCatalog> workflows, List<BusinessObjects.Provider> providers,
      Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    return BusinessObjectUseCases.configuration(new com.appnexus.platform.persistence.businessobject.configuration.MyBatisConfigurationStore(mapper),
        new com.appnexus.platform.persistence.businessobject.configuration.JdbcConfigurationTransaction(manager),
        new com.appnexus.platform.persistence.businessobject.configuration.JacksonConfigurationCodec(),platform,workflows,providers,printing);
  }
  /**
   * 组装编码存储及公开用例，流水与回执保持同事务。
   * @param mapper 与事务共用数据源的固定编码 Mapper
   * @param manager REQUIRED 事务管理器
   * @param platform 可信主体及授权端口
   * @param providers 唯一对象声明
   * @param objects 对象配置公开用例，复核原准入
   * @return 编码公开用例
   */
  public static CodingManagement coding(com.appnexus.platform.persistence.businessobject.coding.CodingMapper mapper, PlatformTransactionManager manager,
      BusinessObjectPlatform platform, List<BusinessObjects.Provider> providers, BusinessObjectManagement objects) {
    return BusinessObjectUseCases.coding(new com.appnexus.platform.persistence.businessobject.coding.MyBatisCodingStore(mapper,manager),platform,providers,objects);
  }
}
