package com.appnexus.platform.tenant.port;
import java.util.function.Supplier;
/** 邀请完整事务输出端口，锁、成员、撤旧授权和审计共同提交。 */
public interface InvitationTransaction {
 /** REQUIRED同数据源事务。 @param work 完整业务工作 @param <T> 返回类型 @return 原结果，异常整笔回滚 */
 <T>T required(Supplier<T> work);
}
