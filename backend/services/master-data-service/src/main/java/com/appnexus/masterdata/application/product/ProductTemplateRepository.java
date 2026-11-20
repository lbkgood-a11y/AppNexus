package com.appnexus.masterdata.application.product;
import java.util.*;
/** 商品模板仓储输出端口；固定租户、版本及项职责，写入和父CAS参与同一完整事务，不开放SQL或自由表名。 */
public interface ProductTemplateRepository {
 /** versions固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> versions(String tenant,String template);
 /** items固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> items(String tenant,String templateVersion);
 /** createTemplate固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param purpose 固定槽用途BASIC、SPEC、QUALITY或EXTENSION，按原业务准入限制
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createTemplate(String id,String tenant,String code,String name,String purpose,String creator,String updater);
 /** purpose固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 String purpose(String tenant,String template);
 /** nextVersion固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Long nextVersion(String tenant,String template);
 /** createVersion固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param purpose 固定槽用途BASIC、SPEC、QUALITY或EXTENSION，按原业务准入限制
  * @param description 版本说明，最多1000字符，按原契约允许空文本
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createVersion(String id,String tenant,String template,long revision,String purpose,String description,String creator,String updater);
 /** versionOwnerAndState固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> versionOwnerAndState(String tenant,String templateVersion);
 /** lockVersionState固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 String lockVersionState(String tenant,String templateVersion);
 /** deleteVersionItems固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int deleteVersionItems(String tenant,String templateVersion);
 /** updateDescription固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param description 版本说明，最多1000字符，按原契约允许空文本
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int updateDescription(String description,String actor,String tenant,String templateVersion);
 /** attributeRevision固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> attributeRevision(String tenant,String revision,String attribute);
 /** attributeGroup固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> attributeGroup(String tenant,String attribute);
 /** createItem固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param id 本操作固定资源UUID文本，已验证格式及父归属
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @param attribute 当前租户稳定属性UUID；修订和选项必须归属该属性
  * @param revision 固定版本UUID或按接口原类型的配置修订号，不跨租户或跨父对象
  * @param dataType 原七种固定属性类型枚举，不接受客户端自由类型
  * @param groupCode 原分组快照编码，可空时沿用原分组查询结果
  * @param groupName 原分组快照名称，固定在模板版本中
  * @param required 模板项是否必填，发布后冻结
  * @param searchable 模板项是否允许原受控搜索，不能开放任意条件
  * @param allowCustom 枚举是否允许原受控自定义文本，不新增平台选项
  * @param sortOrder 显示顺序，单位项，从0开始，按原输入顺序保存
  * @param creator 可信创建账号UUID文本，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID文本，修改信息与业务同事务
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int createItem(String id,String tenant,String templateVersion,String attribute,String revision,String dataType,String groupCode,String groupName,boolean required,boolean searchable,boolean allowCustom,int sortOrder,String creator,String updater);
 /** versionOwners固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<String> versionOwners(String tenant,String templateVersion);
 /** itemCount固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Integer itemCount(String tenant,String templateVersion);
 /** unavailableItemCount固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 Integer unavailableItemCount(String tenant,String templateVersion);
 /** publishVersion固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int publishVersion(String actor,String tenant,String templateVersion);
 /** updateTemplate固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param name 名称，去首尾空白后1至200字符，必填
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param purpose 固定槽用途BASIC、SPEC、QUALITY或EXTENSION，按原业务准入限制
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int updateTemplate(String name,String actor,String tenant,String template,String code,String purpose,long version);
 /** setTemplateStatus固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param enabled 显式目标启停状态；不删除历史或隐式授予其他操作
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int setTemplateStatus(boolean enabled,String actor,String tenant,String template,long version);
 /** retirementOwners固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<String> retirementOwners(String tenant,String templateVersion);
 /** retireVersion固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param templateVersion 当前租户不可变模板版本UUID，不能引用其他模板或租户
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int retireVersion(String actor,String tenant,String templateVersion);
 /** lockTemplate固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> lockTemplate(String tenant,String template);
 /** advanceTemplateVersion固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param actor 认证上下文真实账号UUID文本，必填；审计与业务同事务
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，必须通过对应操作准入
  * @return 原固定事实或受影响行数；CAS写入非1由用例拒绝并回滚
  */
 int advanceTemplateVersion(String actor,String tenant,String template);
 /** summaries固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> summaries(String tenant);
 /** versionState固定存取；父版本、主从及四审计参与完整用例事务，不允许独立提交。
  * @param tenant 服务端可信租户UUID文本，必填，不接受请求体指定
  * @param version 非负父聚合CAS版本，冲突整笔回滚
  * @return 当前租户及父聚合下原固定投影；无匹配返回空，不探测其他租户
  */
 List<Map<String,Object>> versionState(String tenant, String version);
}
