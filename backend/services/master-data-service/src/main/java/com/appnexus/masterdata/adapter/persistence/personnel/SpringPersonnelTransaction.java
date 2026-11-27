package com.appnexus.masterdata.adapter.persistence.personnel;
import com.appnexus.masterdata.organization.port.personnel.PersonnelTransaction;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 人员同数据源REQUIRED事务适配；唯一约束转换为中立异常，业务与审计共同回滚。 */
@Component
public final class SpringPersonnelTransaction implements PersonnelTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 宿主同数据源事务管理器，默认REQUIRED */
 public SpringPersonnelTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){try{return transaction.execute(status->work.get());}catch(DuplicateKeyException conflict){throw new DuplicateRecord(conflict);}}
 public void run(Runnable work){required(()->{work.run();return null;});}
}
