package com.appnexus.workflow.application;
import java.util.function.Supplier;
/** 工作流完整事务输出端口；数据库与引擎推进共用原同连接事务，不假定外部业务副作用可回滚。 */
public interface WorkflowTransaction {
 /** REQUIRED事务；异常回滚请求、任务、引擎与Outbox。 @param work 原完整事务回调 @param <T> 结果类型 @return 原结果 */
 <T>T required(Supplier<T> work);
 /** @param work 无返回完整事务回调，不独立提交状态或回执 */
 default void run(Runnable work){required(()->{work.run();return null;});}
}
