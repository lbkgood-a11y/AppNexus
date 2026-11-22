package com.appnexus.masterdata.application.product;
import com.appnexus.platform.authorization.RowAccessControl.Predicate;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
/** 商品结构固定仓储输出端口，只允许本模块持久化适配器实现；拒绝任意SQL和自由资源映射。 */
public interface ProductStructureRepository {
 /** 固定结构资源。 */
 enum Resource { SPECIFICATION, SKU, COMPONENT }
 /** specifications固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  * @param product 已由调用用例校验的父商品 UUID
  * @param access 由统一执行器按原操作生成的父商品范围
  */
 List<Map<String,Object>> specifications(String tenant,String product,Predicate access);
 /** skus固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  * @param product 已由调用用例校验的父商品 UUID
  * @param access 由统一执行器按原操作生成的父商品范围
  */
 List<Map<String,Object>> skus(String tenant,String product,Predicate access);
 /** components固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  * @param product 已由调用用例校验的父商品 UUID
  * @param access 由统一执行器按原操作生成的父商品范围
  */
 List<Map<String,Object>> components(String tenant,String product,Predicate access);
 /** standardDefinitions固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param configuration 当前固定发布配置UUID，不自动升级
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> standardDefinitions(String tenant,String configuration);
 /** standardOption固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param revision 固定属性修订UUID，必须属于当前租户及配置
  * @param option 固定修订下启用选项UUID；非标准枚举时null
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> standardOption(String tenant,String revision,String option);
 /** createSpecification固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param configuration 当前固定发布配置UUID，不自动升级
  * @param mode 规格模式NONE、STANDARD或CUSTOM，按原业务状态校验
  * @param name 规格名称1至200字符
  * @param description 自定义描述最多2000字符，按原模式必填规则
  * @param signature 按固定字段及排序形成的标准组合文本；CUSTOM为null
  * @param digest 标准组合SHA-256字节；CUSTOM为null，不接受客户端摘要
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int createSpecification(String id,String tenant,String product,String configuration,String mode,String name,String description,String signature,byte[] digest,String creator,String updater);
 /** createStandardValue固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param specification 同父商品规格UUID，不能跨商品引用
  * @param configuration 当前固定发布配置UUID，不自动升级
  * @param item 固定模板项UUID，不接受自由属性路径
  * @param revision 固定属性修订UUID，必须属于当前租户及配置
  * @param type 原固定属性数据类型，互斥值槽由用例校验
  * @param integerValue 有符号64位整数值；仅INTEGER填写，其他类型null
  * @param decimalValue 精确十进制值，精度及小数位来自固定属性定义；其他类型null
  * @param booleanValue 真实布尔值；仅BOOLEAN填写，其他类型null
  * @param option 固定修订下启用选项UUID；非标准枚举时null
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @param product 已取得同事务写许可的父商品 UUID，SQL 同时验证子记录从属关系
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int createStandardValue(String id,String tenant,String specification,String configuration,String item,String revision,String type,Long integerValue,BigDecimal decimalValue,Boolean booleanValue,String option,String creator,String updater,String product);
 /** customDefinitions固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param configuration 当前固定发布配置UUID，不自动升级
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> customDefinitions(String tenant,String configuration);
 /** validOptionCount固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param revision 固定属性修订UUID，必须属于当前租户及配置
  * @param option 固定修订下启用选项UUID；非标准枚举时null
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 Integer validOptionCount(String tenant,String revision,String option);
 /** createCustomValue固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param specification 同父商品规格UUID，不能跨商品引用
  * @param configuration 当前固定发布配置UUID，不自动升级
  * @param item 固定模板项UUID，不接受自由属性路径
  * @param revision 固定属性修订UUID，必须属于当前租户及配置
  * @param type 原固定属性数据类型，互斥值槽由用例校验
  * @param text 最多2000字符；仅TEXT填写，其他类型null
  * @param dateValue ISO自然日LocalDate，无时区；仅DATE填写，其他类型null
  * @param integerValue 有符号64位整数值；仅INTEGER填写，其他类型null
  * @param decimalValue 精确十进制值，精度及小数位来自固定属性定义；其他类型null
  * @param booleanValue 真实布尔值；仅BOOLEAN填写，其他类型null
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @param product 已取得同事务写许可的父商品 UUID，SQL 同时验证子记录从属关系
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int createCustomValue(String id,String tenant,String specification,String configuration,String item,String revision,String type,String text,LocalDate dateValue,Long integerValue,BigDecimal decimalValue,Boolean booleanValue,String creator,String updater,String product);
 /** createChoice固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param value 同一规格属性值UUID，不能跨规格引用
  * @param revision 固定属性修订UUID，必须属于当前租户及配置
  * @param type 原固定属性数据类型，互斥值槽由用例校验
  * @param option 固定修订下启用选项UUID；非标准枚举时null
  * @param customText 原模板允许的枚举自定义文本，非自定义选择时null，不新建选项
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @param product 已取得同事务写许可的父商品 UUID，SQL 同时验证子记录从属关系
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int createChoice(String id,String tenant,String value,String revision,String type,String option,String customText,String creator,String updater,String product);
 /** specificationValues固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param specification 同父商品规格UUID，不能跨商品引用
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  * @param product 已由调用用例校验的父商品 UUID
  * @param access 由统一执行器按原操作生成的父商品范围
  */
 List<Map<String,Object>> specificationValues(String tenant,String specification,String product,Predicate access);
 /** customChoices固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param value 同一规格属性值UUID，不能跨规格引用
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  * @param product 已由调用用例校验的父商品 UUID
  * @param access 由统一执行器按原操作生成的父商品范围
  */
 List<Map<String,Object>> customChoices(String tenant,String value,String product,Predicate access);
 /** standardChoices固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param option 固定修订下启用选项UUID；非标准枚举时null
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> standardChoices(String tenant,String option);
 /** specificationCount固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param specification 同父商品规格UUID，不能跨商品引用
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 Integer specificationCount(String tenant,String product,String specification);
 /** createSku固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param specification 同父商品规格UUID，不能跨商品引用
  * @param code SKU固定业务编码，创建后不可修改
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int createSku(String id,String tenant,String product,String specification,String code,String creator,String updater);
 /** unitPrecision固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param unit 当前租户单位UUID，按原形式校验
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 Integer unitPrecision(String tenant,String unit);
 /** 在父商品锁和版本保护下替换计量配置。 */
 int saveMeasurement(String tenant,String product,String configuration);
 /** 查询是否存在可用的组内直连换算。 */
 boolean hasMeasurementConversion(String tenant,String source,String target);
 /** componentChildren固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<String> componentChildren(String tenant,String product);
 /** existingComponent固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param component 当前租户组成商品UUID，禁止自环及组合图循环
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> existingComponent(String tenant,String product,String component);
 /** updateComponent固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param quantity 组成品基础单位下的精确正数，受原单位精度约束
  * @param componentVersion 组成商品当前非负版本，启用及版本必须仍匹配
  * @param actor 可信实际账号UUID，不能由客户端指定
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param product 已取得同事务写许可的父商品 UUID，SQL 同时验证子记录从属关系
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int updateComponent(BigDecimal quantity,long componentVersion,String actor,String tenant,String id,String product);
 /** createComponent固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param component 当前租户组成商品UUID，禁止自环及组合图循环
  * @param quantity 组成品基础单位下的精确正数，受原单位精度约束
  * @param componentVersion 组成商品当前非负版本，启用及版本必须仍匹配
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int createComponent(String id,String tenant,String product,String component,BigDecimal quantity,long componentVersion,String creator,String updater);
 /** ensureGraphGuard固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务同事务
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int ensureGraphGuard(String tenant,String creator,String updater);
 /** lockGraphGuard固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> lockGraphGuard(String tenant);
 /** advanceParentVersion固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param actor 可信实际账号UUID，不能由客户端指定
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param version 原非负父商品CAS，非1更新整笔回滚
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int advanceParentVersion(String actor,String tenant,String product,long version);
 /** state固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param kind 固定specification、sku、component之一，不允许自由表名
  * @param enabled 显式目标状态，不删除历史
  * @param actor 可信实际账号UUID，不能由客户端指定
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param product 已授权当前租户父商品UUID
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param version 原非负父商品CAS，非1更新整笔回滚
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 int state(String kind, boolean enabled, String actor, String tenant, String product, String id, long version);
 /** parent固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @param lock 是否持有原写事务行锁，不对客户端开放
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  * @param access 由统一执行器按原操作生成的父商品范围
  */
 List<Map<String,Object>> parent(String tenant, String id, boolean lock,Predicate access);
 /** audit固定存取；父CAS、结构正文及审计参与同一用例事务。
  * @param resource 固定结构资源枚举，不暴露数据库表名
  * @param tenant 可信租户UUID文本，必填；不能客户端指定
  * @param id 当前操作资源UUID，必须在已授权父聚合内
  * @return 原固定投影或受影响行数；写入失败必须由用例整笔回滚
  */
 List<Map<String,Object>> audit(Resource resource,String tenant,String id);
}
