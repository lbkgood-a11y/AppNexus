package com.appnexus.leave.adapter.transaction;
import com.appnexus.leave.application.LeaveTransaction;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** 原请假宿主REQUIRED事务，异常传播不部分提交业务或审批记录。 */
public final class SpringLeaveTransaction implements LeaveTransaction {
 private final TransactionTemplate transaction;
 /** @param manager 原宿主同一数据源事务管理器 */
 public SpringLeaveTransaction(PlatformTransactionManager manager){transaction=new TransactionTemplate(manager);}
 public <T>T required(Supplier<T> work){return transaction.execute(status->work.get());}
}
