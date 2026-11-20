package com.appnexus.platform.persistence.configuration;
import com.appnexus.platform.configuration.TenantParameters;
import com.appnexus.platform.configuration.port.TenantParameterTransaction;
import com.appnexus.platform.configuration.TenantParameters.Rejection;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
/** 参数同数据源事务，唯一冲突保留原稳定拒绝码，审计失败不能部分提交。 */
@Component
public final class SpringTenantParameterTransaction implements TenantParameterTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 原宿主同数据源REQUIRED事务管理器 */
 public SpringTenantParameterTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){try{return transaction.execute(status->work.get());}catch(DuplicateKeyException conflict){throw new Rejection(409,"PARAMETER_CHANGED");}}
}
