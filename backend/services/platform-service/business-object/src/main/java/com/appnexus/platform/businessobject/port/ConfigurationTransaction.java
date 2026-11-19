package com.appnexus.platform.businessobject.port;

import java.util.function.Supplier;

/** 对象配置用例事务端口；嵌套调用加入当前事务，异常必须回滚配置、解析与审计。 */
public interface ConfigurationTransaction {
  /**
   * 在 REQUIRED 语义事务中执行写用例，禁止单条 SQL 独立提交。
   * @param work 已完成上下文建立的业务回调；异常向上传播并回滚
   */
  void run(Runnable work);

  /**
   * 在 REQUIRED 语义事务中执行有返回值的用例。
   * @param <T> 不依赖存储技术的业务结果类型
   * @param work 业务回调，必须与配置存储共享连接和事务管理器
   * @return 回调结果；不吞掉业务异常
   */
  <T> T call(Supplier<T> work);
}
