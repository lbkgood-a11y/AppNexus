package com.appnexus.masterdata.application.product;
import java.util.*;
/** 属性聚合固定仓储输出端口；父版本、修订、选项与审计同事务，不开放SQL或任意表名。 */
public interface ProductAttributeRepository {
 /** revisions固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> revisions(String tenant,String attribute);
 /** options固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> options(String tenant,String revision);
 /** createGroup固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createGroup(String id,String tenant,String code,String name,String creator,String updater);
 /** createAttribute固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param group 当前租户属性分组UUID，可空表示未分组
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createAttribute(String id,String tenant,String code,String group,String creator,String updater);
 /** enabledUnitCount固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param unit 当前租户单位UUID；按原属性或商品必填规则允许缺省
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Integer enabledUnitCount(String tenant,String unit);
 /** nextRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Long nextRevision(String tenant,String attribute);
 /** createRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @param revisionNumber 父锁内分配的正整数修订号，不由客户端指定
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param dataType 原七种固定属性类型枚举，不接受客户端自由类型
  * @param unit 当前租户单位UUID；按原属性或商品必填规则允许缺省
  * @param scale 数值精度，单位小数位，0至12；非数值类型必须null
  * @param rulesJson 固定白名单属性规则JSON；null表示空规则，未知键和错误类型拒绝
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createRevision(String id,String tenant,String attribute,long revisionNumber,String name,String dataType,String unit,Integer scale,String rulesJson,String creator,String updater);
 /** createInitialOption固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param label 枚举显示文本，1至200字符，不执行表达式
  * @param sortOrder 显示顺序，单位项，从0开始，按原输入顺序保存
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createInitialOption(String id,String tenant,String revision,String code,String label,int sortOrder,String creator,String updater);
 /** optionCount固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Integer optionCount(String tenant,String revision);
 /** publishRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int publishRevision(String actor,String tenant,String revision);
 /** createOption固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param label 枚举显示文本，1至200字符，不执行表达式
  * @param sortOrder 显示顺序，单位项，从0开始，按原输入顺序保存
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createOption(String id,String tenant,String revision,String code,String label,int sortOrder,String creator,String updater);
 /** updateGroup固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param group 当前租户属性分组UUID，可空表示未分组
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int updateGroup(String name,String actor,String tenant,String group,String code,long version);
 /** updateAttribute固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param group 当前租户属性分组UUID，可空表示未分组
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int updateAttribute(String group,String actor,String tenant,String attribute,String code,long version);
 /** revisionOwners固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<String> revisionOwners(String tenant,String revision);
 /** retireRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int retireRevision(String actor,String tenant,String revision);
 /** validUnitCount固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param unit 当前租户单位UUID；按原属性或商品必填规则允许缺省
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Integer validUnitCount(String tenant,String unit);
 /** updateRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param dataType 原七种固定属性类型枚举，不接受客户端自由类型
  * @param unit 当前租户单位UUID；按原属性或商品必填规则允许缺省
  * @param scale 数值精度，单位小数位，0至12；非数值类型必须null
  * @param rulesJson 固定白名单属性规则JSON；null表示空规则，未知键和错误类型拒绝
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int updateRevision(String name,String dataType,String unit,Integer scale,String rulesJson,String actor,String tenant,String revision);
 /** deleteOptions固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int deleteOptions(String tenant,String revision);
 /** replaceOption固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param label 枚举显示文本，1至200字符，不执行表达式
  * @param sortOrder 显示顺序，单位项，从0开始，按原输入顺序保存
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int replaceOption(String id,String tenant,String revision,String code,String label,int sortOrder,String creator,String updater);
 /** lockAttribute固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> lockAttribute(String tenant,String attribute);
 /** advanceAttributeVersion固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int advanceAttributeVersion(String actor,String tenant,String attribute);
 /** revisionParent固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> revisionParent(String tenant,String revision);
 /** lockRevisionState固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 String lockRevisionState(String tenant,String revision);
 /** groups固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> groups(String tenant);
 /** attributes固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> attributes(String tenant);
 /** status固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param group 当前租户属性分组UUID，可空表示未分组
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param enabled 显式目标启停状态；不删除历史或隐式授予其他操作
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int status(boolean group, String tenant, String id, boolean enabled, String actor, long version);
}
