package com.appnexus.masterdata.application.product;
import java.util.function.Supplier;
/** 商品目录事务与原技术冲突映射输出端口，只允许本模块事务适配器实现。 */
public interface ProductCatalogTransaction {
 /** REQUIRED同连接事务，分类锚点锁、版本、快照和审计共同提交。 @param work 完整业务工作 @param <T> 结果类型 @return 成功结果，异常整笔回滚 */
 <T>T required(Supplier<T> work);
 /** 保留原目录操作的完整性冲突映射，本方法不另开事务。 @param operation 原用例选择的冲突保护范围 @param <T> 结果类型 @return 原结果，数据库完整性冲突转换原稳定拒绝码 */
 <T>T integrity(Supplier<T> operation);
}
