package com.appnexus.platform.configuration.port;
import java.util.function.Supplier;
/** 参数完整事务输出端口，不向应用暴露Spring事务或数据库异常。 */
public interface TenantParameterTransaction {
 /** REQUIRED同连接事务，租户锁、CAS与审计共同提交；唯一冲突转换原PARAMETER_CHANGED。 @param work 完整参数保存工作 @param <T> 结果类型 @return 成功结果，失败整笔回滚 */
 <T>T required(Supplier<T> work);
}
