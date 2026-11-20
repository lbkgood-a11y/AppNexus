package com.appnexus.printing.adapter.transaction;
import com.appnexus.printing.application.PrintTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 打印同数据源REQUIRED事务；嵌套加入原调用链，异常不吞掉、不独立提交请求或任务。 */
public final class SpringPrintTransaction implements PrintTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 原宿主同一数据源事务管理器 */
 public SpringPrintTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){return transaction.execute(status->work.get());}
}
