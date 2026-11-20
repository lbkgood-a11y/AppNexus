package com.appnexus.masterdata.adapter.transaction;
import com.appnexus.masterdata.application.product.ProductCatalogTransaction;
import com.appnexus.masterdata.api.masterdata.MasterData;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 目录事务技术适配；保留原完整性保护范围，不将所有读取或状态拒绝改写为同一错误。 */
@Component
public final class SpringProductCatalogTransaction implements ProductCatalogTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 同数据源REQUIRED事务管理器 */
 public SpringProductCatalogTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){return transaction.execute(status->work.get());}
 public <T>T integrity(Supplier<T> operation){try{return operation.get();}catch(DataIntegrityViolationException conflict){throw new MasterData.Rejection(409,"PRODUCT_CATALOG_CONFLICT");}}
}
