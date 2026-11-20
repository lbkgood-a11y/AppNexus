package com.appnexus.platform.configuration.port;
import java.util.function.Supplier;
/** 引用登记事务端口，只允许平台持久化适配器实现。 */
public interface PartnerReferenceTransaction {
 /** 使用同一数据源 REQUIRED 事务；嵌套加入外层，运行异常整笔回滚，不能独立提交。 @param action 同事务客户端锁、字典锁、引用及幂等写入 @param <T> 用例结果类型 @return 成功提交对应结果 */
 <T>T required(Supplier<T> action);
}
