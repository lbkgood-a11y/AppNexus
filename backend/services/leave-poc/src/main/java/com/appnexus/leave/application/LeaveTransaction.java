package com.appnexus.leave.application;
import java.util.function.Supplier;
/** 请假完整用例事务输出端口，不向业务层泄漏Spring状态对象。 */
public interface LeaveTransaction {
 /** REQUIRED同数据源事务；单据依据、公共对象执行及审批轮次共同回滚，嵌套加入外层。 @param work 完整业务工作 @param <T> 返回类型 @return 原用例结果 */
 <T>T required(Supplier<T> work);
}
