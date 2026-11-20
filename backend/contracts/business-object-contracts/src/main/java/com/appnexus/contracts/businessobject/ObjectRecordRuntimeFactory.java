package com.appnexus.contracts.businessobject;

/** 宿主将对象的构建期映射编译为通用运行器；业务对象无需实现逐能力取数及回执SQL。 */
public interface ObjectRecordRuntimeFactory {
  /**
   * @param provider 唯一对象声明；不得由浏览器构造
   * @param mapping 对象所有者代码固定的映射，不接受任意SQL
   * @param hook 对象特殊业务前置约束；不能增加授权放行分支
   * @return 受租户、版本和状态约束的公共运行器
   */
  BusinessObjectRuntime create(BusinessObjects.Provider provider,ObjectRecordMapping mapping,Hook hook);
  /** 只有确实存在特殊业务规则时实现；标准对象使用空约束即可。 */
  @FunctionalInterface interface Hook {
    void before(BusinessObjects.Context context,String id,String operation,InteractionContract.Service service);
  }
}
