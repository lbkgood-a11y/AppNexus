package com.appnexus.printing.application;
import java.util.function.Supplier;
/** 打印完整用例事务输出端口，只有本模块事务适配器实现。 */
public interface PrintTransaction {
 /** REQUIRED同连接事务；请求去重、任务、确认及审计共同提交，异常全部回滚。 @param work 完整打印用例 @param <T> 结果类型 @return 原用例结果 */
 <T>T required(Supplier<T> work);
 /** @param work 无返回完整用例，加入原事务，失败整笔回滚 */
 default void run(Runnable work){required(()->{work.run();return null;});}
}
