package com.appnexus.platform.persistence.businessobject;

import com.appnexus.platform.businessobject.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.WorkflowDefinitionCatalog;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 对象能力的 JDBC 装配入口；生产与数据库测试共用，返回值仅为公开用例。 */
public final class BusinessObjectPersistence {
  private BusinessObjectPersistence() {}
  /**
   * 组装真实配置存储及事务；不承担业务决策。
   * @param jdbc 对象配置数据源客户端，必须与 manager 共用数据源
   * @param manager REQUIRED 事务管理器，配置、审计及解析共同回滚
   * @param platform 可信主体和实时授权端口
   * @param workflows 延迟流程目录，关闭时允许 null
   * @param providers 唯一对象声明集合
   * @return 配置公开用例；默认未装配打印能力
   */
  public static BusinessObjectManagement create(JdbcTemplate jdbc, PlatformTransactionManager manager,
      BusinessObjectPlatform platform, Supplier<WorkflowDefinitionCatalog> workflows, List<BusinessObjects.Provider> providers) {
    return create(jdbc,manager,platform,workflows,providers,()->null);
  }
  /**
   * 组装配置用例与可选打印目录，打印能力关闭不影响直接业务操作。
   * @param jdbc 与事务共用数据源的固定 JDBC 客户端
   * @param manager REQUIRED 事务管理器，异常整笔回滚
   * @param platform 可信主体及实时授权端口
   * @param workflows 延迟流程目录，关闭时允许 null
   * @param providers 唯一业务对象声明
   * @param printing 延迟打印公开能力，关闭时允许 null
   * @return 配置公开用例
   */
  public static BusinessObjectManagement create(JdbcTemplate jdbc, PlatformTransactionManager manager,
      BusinessObjectPlatform platform, Supplier<WorkflowDefinitionCatalog> workflows, List<BusinessObjects.Provider> providers,
      Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    return BusinessObjectUseCases.configuration(new com.appnexus.platform.persistence.businessobject.configuration.JdbcConfigurationStore(jdbc),
        new com.appnexus.platform.persistence.businessobject.configuration.JdbcConfigurationTransaction(manager),
        new com.appnexus.platform.persistence.businessobject.configuration.JacksonConfigurationCodec(),platform,workflows,providers,printing);
  }
  /**
   * 组装编码存储及公开用例，流水与回执保持同事务。
   * @param jdbc 与事务共用数据源的 JDBC 客户端
   * @param manager REQUIRED 事务管理器
   * @param platform 可信主体及授权端口
   * @param providers 唯一对象声明
   * @param objects 对象配置公开用例，复核原准入
   * @return 编码公开用例
   */
  public static CodingManagement coding(JdbcTemplate jdbc, PlatformTransactionManager manager,
      BusinessObjectPlatform platform, List<BusinessObjects.Provider> providers, BusinessObjectManagement objects) {
    return BusinessObjectUseCases.coding(new com.appnexus.platform.persistence.businessobject.coding.JdbcCodingStore(jdbc,manager),platform,providers,objects);
  }
}
