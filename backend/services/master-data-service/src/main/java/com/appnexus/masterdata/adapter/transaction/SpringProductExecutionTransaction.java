package com.appnexus.masterdata.adapter.transaction;
import com.appnexus.masterdata.application.product.ProductExecutionTransaction;
import com.appnexus.masterdata.api.masterdata.MasterData;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.dao.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** 主档事务技术适配；完整性、锁、超时沿用原拒绝，不能在应用层引入Spring异常。 */
@Component
public final class SpringProductExecutionTransaction implements ProductExecutionTransaction {
 private final TransactionTemplate write;private final TransactionTemplate readOnly;private final TransactionTemplate migration;
 /** @param manager 同数据源REQUIRED事务管理器；迁移超时固定10秒，与原实现保持一致 */
 public SpringProductExecutionTransaction(PlatformTransactionManager manager){write=new TransactionTemplate(manager);readOnly=new TransactionTemplate(manager);readOnly.setReadOnly(true);migration=new TransactionTemplate(manager);migration.setTimeout(10);}
 public <T>T write(Supplier<T> work){try{return write.execute(status->work.get());}catch(DataIntegrityViolationException conflict){throw new MasterData.Rejection(409,"PRODUCT_V1_CONFLICT");}}
 public <T>T readOnly(Supplier<T> work){return readOnly.execute(status->work.get());}
 public <T>T migrate(Supplier<T> work){try{return migration.execute(status->work.get());}catch(PessimisticLockingFailureException|QueryTimeoutException|TransactionTimedOutException busy){throw new MasterData.Rejection(409,"PRODUCT_MIGRATION_IN_PROGRESS");}catch(DataIntegrityViolationException conflict){throw new MasterData.Rejection(409,"PRODUCT_MIGRATION_CONFLICT");}}
}
