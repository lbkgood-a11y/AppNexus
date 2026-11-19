package com.appnexus.masterdata.application;

import java.util.function.Supplier;

/** 用例事务端口；父记录、关系、字典引用及审计必须同事务提交，禁止异步逃逸。 */
public interface RecordTransaction {
  /**
   * 执行同数据源事务，任何业务或技术异常均回滚。
   * @param work 当前线程中的聚合用例
   * @param duplicateCode 唯一约束冲突映射的业务码；null 表示保留原技术异常
   * @return 事务提交后的用例结果
   */
  <T>T execute(Supplier<T> work,String duplicateCode);
}
