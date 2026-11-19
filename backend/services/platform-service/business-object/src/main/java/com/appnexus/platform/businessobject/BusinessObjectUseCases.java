package com.appnexus.platform.businessobject;

import com.appnexus.platform.businessobject.port.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.WorkflowDefinitionCatalog;
import java.util.List;
import java.util.function.Supplier;

/** 业务对象核心的无框架装配入口；外部只得到公开用例，不暴露 internal 实现。 */
public final class BusinessObjectUseCases {
  private BusinessObjectUseCases() {}
  /**
   * 组装配置用例；端口必须由受信任宿主提供，不自行创建技术实现。
   * @param store 固定配置存储端口，与 transaction 共用数据源
   * @param transaction 完整用例事务，异常回滚且嵌套加入原事务
   * @param codec 版本化快照编解码端口
   * @param platform 可信主体与授权端口，每次执行复核
   * @param workflows 延迟流程目录，关闭时允许返回 null
   * @param providers 唯一代码声明，冲突拒绝装配
   * @param printing 延迟打印公开能力，关闭时允许返回 null
   * @return 配置公开用例，不暴露内部实现类型
   */
  public static BusinessObjectManagement configuration(ConfigurationStore store, ConfigurationTransaction transaction,
      ConfigurationCodec codec, BusinessObjectPlatform platform, Supplier<WorkflowDefinitionCatalog> workflows,
      List<BusinessObjects.Provider> providers, Supplier<com.appnexus.contracts.printing.BusinessPrinting> printing) {
    var service = new com.appnexus.platform.businessobject.internal.application.configuration.BusinessObjectService(store,transaction,codec,platform,workflows,providers);
    service.printing(printing);
    return service;
  }
  /**
   * 组装编码用例，沿用真实对象准入，不能因发号绕过原业务权限。
   * @param store 同事务锁定流水及回执的存储端口
   * @param platform 可信主体与实时授权端口
   * @param providers 唯一对象与编码字段声明
   * @param objects 对象配置公开用例，用于复核配置与页面操作准入
   * @return 编码公开用例
   */
  public static CodingManagement coding(CodingStore store, BusinessObjectPlatform platform,
      List<BusinessObjects.Provider> providers, BusinessObjectManagement objects) {
    return new com.appnexus.platform.businessobject.internal.application.coding.CodingService(store,platform,providers,
        new com.appnexus.platform.businessobject.internal.application.access.ExistingCodingObjectAccess(objects));
  }
}
