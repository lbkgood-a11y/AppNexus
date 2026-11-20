package com.appnexus.platform.persistence.configuration;
import com.appnexus.platform.configuration.port.PartnerReferenceTransaction;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 引用登记同数据源 REQUIRED 事务；异常传播并回滚业务、审计及幂等回执。 */
@Component
public final class SpringPartnerReferenceTransaction implements PartnerReferenceTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 宿主同一数据源事务管理器，默认 REQUIRED，禁止独立提交 */
 public SpringPartnerReferenceTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> action){return transaction.execute(status->action.get());}
}
