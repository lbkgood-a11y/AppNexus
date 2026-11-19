package com.appnexus.platform.persistence.businessobject.configuration;

import com.appnexus.platform.businessobject.port.ConfigurationTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring 事务适配；复用原 REQUIRED 传播，保证嵌套管控与追加审计原子提交。 */
public final class JdbcConfigurationTransaction implements ConfigurationTransaction {
  private final TransactionTemplate transaction;

  /** @param manager 与 ConfigurationStore 同数据源的事务管理器，禁止每个写操作独立连接 */
  public JdbcConfigurationTransaction(PlatformTransactionManager manager) {
    transaction = new TransactionTemplate(manager);
  }

  /** {@inheritDoc} */
  @Override public void run(Runnable work) {
    transaction.executeWithoutResult(status -> work.run());
  }

  /** {@inheritDoc} */
  @Override public <T> T call(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }
}
