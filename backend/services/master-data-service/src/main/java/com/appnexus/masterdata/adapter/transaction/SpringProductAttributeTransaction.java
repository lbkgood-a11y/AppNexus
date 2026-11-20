package com.appnexus.masterdata.adapter.transaction;
import com.appnexus.masterdata.application.product.ProductAttributeTransaction;
import com.appnexus.masterdata.api.masterdata.MasterData;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 属性写事务及技术异常转换；保留原PRODUCT_ATTRIBUTE_CONFLICT拒绝和整笔回滚语义。 */
@Component
public final class SpringProductAttributeTransaction implements ProductAttributeTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 同一数据源事务管理器，默认REQUIRED */
 public SpringProductAttributeTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T write(Supplier<T> work){try{return transaction.execute(status->work.get());}catch(DataIntegrityViolationException conflict){throw new MasterData.Rejection(409,"PRODUCT_ATTRIBUTE_CONFLICT");}}
}
