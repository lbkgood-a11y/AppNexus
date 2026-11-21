package com.appnexus.masterdata.application.product;
import java.util.*;
/** 商品目录固定仓储输出端口；分类树锚点锁、版本与发布快照参与原完整事务，不开放SQL。 */
public interface ProductCatalogRepository {
 /** createUnit固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param dimension 单位维度固定编码，1至64字符，创建后不可修改
  * @param scale 数值精度，单位小数位，0至12；非数值类型必须null
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createUnit(String id,String tenant,String code,String name,String dimension,int scale,String creator,String updater);
 /** renameUnit固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param unit 当前租户单位UUID；按原属性或商品必填规则允许缺省
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param dimension 单位维度固定编码，1至64字符，创建后不可修改
  * @param scale 数值精度，单位小数位，0至12；非数值类型必须null
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int renameUnit(String name,String actor,String tenant,String unit,String code,String dimension,int scale,long version);
 /** setUnitStatus固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param enabled 显式目标启停状态；不删除历史或隐式授予其他操作
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param unit 当前租户单位UUID；按原属性或商品必填规则允许缺省
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int setUnitStatus(boolean enabled,String actor,String tenant,String unit,long version);
 /** createCategory固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param parent 当前租户父分类UUID，null表示根；禁止环、越级及跨租户
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createCategory(String id,String tenant,String code,String name,String parent,String creator,String updater);
 /** moveCategory固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param parent 当前租户父分类UUID，null表示根；禁止环、越级及跨租户
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param category 当前租户分类UUID，分类树和锚点锁约束由原用例执行
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int moveCategory(String name,String parent,String actor,String tenant,String category,long version);
 /** setCategoryStatus固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param enabled 显式目标启停状态；不删除历史或隐式授予其他操作
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param category 当前租户分类UUID，分类树和锚点锁约束由原用例执行
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int setCategoryStatus(boolean enabled,String actor,String tenant,String category,long version);
 /** publishedTemplate固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> publishedTemplate(String tenant,String templateVersion);
 /** templateAttributes固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<String> templateAttributes(String tenant,String templateVersion);
 /** nextConfigurationRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param category 当前租户分类UUID，分类树和锚点锁约束由原用例执行
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Long nextConfigurationRevision(String tenant,String category);
 /** createConfiguration固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param category 当前租户分类UUID，分类树和锚点锁约束由原用例执行
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param path 原分类树生成的显示路径快照，不由客户端直接指定
  * @param serviceUnitRequired 服务形式是否要求基础单位，随配置发布冻结
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createConfiguration(String id,String tenant,String category,long revision,String path,boolean serviceUnitRequired,String creator,String updater);
 /** bindTemplate固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param configuration 已发布分类配置UUID，版本固定，不自动升级
  * @param purpose 固定槽用途BASIC、SPEC、QUALITY或EXTENSION，按原业务准入限制
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int bindTemplate(String id,String tenant,String configuration,String purpose,String templateVersion,String creator,String updater);
 /** freezeConfigurationItem固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param configuration 已发布分类配置UUID，版本固定，不自动升级
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int freezeConfigurationItem(String creator,String updater,String tenant,String configuration);
 /** activateConfiguration固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param configuration 已发布分类配置UUID，版本固定，不自动升级
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param category 当前租户分类UUID，分类树和锚点锁约束由原用例执行
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int activateConfiguration(String configuration,String actor,String tenant,String category,long version);
 /** ensureCategoryGuard固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int ensureCategoryGuard(String tenant,String creator,String updater);
 /** lockCategoryGuard固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 String lockCategoryGuard(String tenant);
 /** units固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param keyword 编码或名称字面关键词，最多200字符，空文本表示不筛选
  * @param size 每页1至100条
  * @param offset 从0开始非负行偏移
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> units(String tenant, String keyword, String group, int size, int offset);
 /** 读取可信租户的分组目录。 */
 List<Map<String,Object>> unitGroups(String tenant);
 /** 在单位创建事务内补全旧客户端提交的目录键。 */
 void ensureUnitGroup(String tenant,String code,String actor);
 /** 在目录写入事务内创建分组。 */
 void createUnitGroup(String tenant,String code,String name,String actor);
 /** 使用当前版本修改分组名称。 */
 int renameUnitGroup(String tenant,String code,String name,String actor,long version);
 /** unit固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> unit(String tenant, String id);
 /** configuration固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param category 当前租户分类UUID，分类树和锚点锁约束由原用例执行
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> configuration(String tenant, String category, String id);
 /** tree固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> tree(String tenant);
 /** slots固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> slots(String tenant, String id);
 /** unitCount固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param keyword 编码或名称字面关键词，最多200字符，空文本表示不筛选
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 long unitCount(String tenant, String keyword, String group);
}
