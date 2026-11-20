package com.appnexus.masterdata.application.product;
import java.util.function.Supplier;
/** 商品模板完整写事务输出端口，只允许本模块事务适配器实现。 */
public interface ProductTemplateTransaction {
 /** REQUIRED同连接事务；嵌套加入现有事务，模板父版本、项、发布与审计共同提交，完整性冲突转换原稳定业务码。 @param work 完整模板用例 @param <T> 结果类型 @return 成功结果，失败整笔回滚 */
 <T>T write(Supplier<T> work);
}
