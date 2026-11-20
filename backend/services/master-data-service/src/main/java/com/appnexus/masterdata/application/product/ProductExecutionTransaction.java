package com.appnexus.masterdata.application.product;
import java.util.function.Supplier;
/** 商品主档完整事务输出端口，只允许本模块事务技术适配器实现。 */
public interface ProductExecutionTransaction {
 /** REQUIRED写事务，父CAS、主从、历史、审计共同回滚，完整性冲突沿用原码。 @param work 完整主档写操作 @param <T> 结果类型 @return 提交结果 */
 <T>T write(Supplier<T> work);
 /** 原只读预览事务，不创建临时记录。 @param work 授权后只读预览 @param <T> 结果类型 @return 预览结果，不保证后续执行必成功 */
 <T>T readOnly(Supplier<T> work);
 /** REQUIRED迁移事务，超时10秒，保留原并发/超时及完整性拒绝；成功重放不重复写入。 @param work 完整结构迁移 @param <T> 结果类型 @return 迁移或重放结果 */
 <T>T migrate(Supplier<T> work);
}
