package com.appnexus.contracts.operation;
import java.lang.annotation.*;
/** 模块声明其真实业务/系统对象元数据；不提供默认分类，不由控制器名称推断业务性质。 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessObjectType {
 /** 稳定对象编码，必须与该模块操作声明一致。 */
 String code();
 /** 中文对象名称。 */
 String nameZh();
 /** 英文对象名称。 */
 String nameEn();
 /** 对象业务性质，不因支持审批或打印而改变。 */
 Category category();
 /** 代码所属业务模块标识。 */
 String module();
 /** 分类为对象语义，不能作为权限或业务执行依据。 */
 enum Category { MASTER_DATA, TRANSACTION, CONFIGURATION, LEDGER, SYSTEM }
}
