package com.appnexus.masterdata.adapter.transaction;

import com.appnexus.masterdata.application.RecordTransaction;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 宿主 Spring 事务适配器；沿用 REQUIRED 传播，使字典、父对象与明细共同回滚。 */
public final class SpringRecordTransaction implements RecordTransaction {
  private final TransactionTemplate transaction;
  /** @param manager 与所有主数据输出适配器共用的事务管理器 */
  public SpringRecordTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
  @Override public <T>T execute(Supplier<T> work,String duplicateCode){
    try{return transaction.execute(status->work.get());}
    catch(DuplicateKeyException e){if(duplicateCode==null)throw e;throw new Rejection(409,duplicateCode);}
  }
}
