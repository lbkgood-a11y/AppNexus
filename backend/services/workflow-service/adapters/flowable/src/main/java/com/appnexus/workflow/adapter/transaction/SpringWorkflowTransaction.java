package com.appnexus.workflow.adapter.transaction;
import com.appnexus.workflow.application.WorkflowTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 工作流同数据源REQUIRED事务技术适配，不改变原租约领取和事务外业务调用边界。 */
public final class SpringWorkflowTransaction implements WorkflowTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 原数据库与Flowable共用的事务管理器 */
 public SpringWorkflowTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){return transaction.execute(status->work.get());}
}
