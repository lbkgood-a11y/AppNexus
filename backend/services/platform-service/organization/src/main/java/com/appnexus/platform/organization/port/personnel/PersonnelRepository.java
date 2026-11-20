package com.appnexus.platform.organization.port.personnel;
import java.util.*;
/** 人员明细及关联仓储输出端口；仅允许平台持久化适配器实现。 */
public interface PersonnelRepository {
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @param section 固定明细分类编码，不作为SQL标识符
  * @return 匹配投影；不存在不跨租户探测
  */
 List<String> readDetailContent(String tenant,String person,String id,String section);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param section 固定明细分类编码，不作为SQL标识符
  * @param content 经分类白名单验证的固定明细内容
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int createDetail(String tenant,String id,String person,String section,String content);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param content 经分类白名单验证的固定明细内容
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @param section 固定明细分类编码，不作为SQL标识符
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int updateDetail(String content,String tenant,String person,String id,String section);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @param section 固定明细分类编码，不作为SQL标识符
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int deleteDetail(String tenant,String person,String id,String section);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param member 当前租户成员UUID，不能替换为全局账号UUID
  * @param actor 可信实际账号UUID，审计身份不能由客户端指定
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int createAssignment(String tenant,String id,String person,String member,String actor);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param member 当前租户成员UUID，不能替换为全局账号UUID
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int updateAssignment(String member,String tenant,String person,String id);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param id 当前父人员下明细或关联UUID，禁止裸ID越权访问
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int deleteAssignment(String tenant,String person,String id);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param member 当前租户成员UUID，不能替换为全局账号UUID
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 Integer countActiveIdentity(String tenant,String person,String member);
 /** 固定人员存取；写入与父版本、字典引用及审计同事务。
  * @param actor 可信实际账号UUID，审计身份不能由客户端指定
  * @param tenant 服务端可信租户UUID，禁止请求体指定
  * @param person 当前租户人员UUID，先经操作权限及归属校验
  * @param version 当前非负人员版本，用于CAS；成功递增1
  * @return 匹配或受影响行数；CAS失败由用例拒绝整笔事务
  */
 int advanceVersion(String actor,String tenant,String person,long version);
 /** 已授权人员固定投影。 @param tenant 可信租户UUID @param id 已授权父人员UUID @return 当前租户和父人员下的稳定投影 */
 List<Map<String,Object>> profile(String tenant,String id);
 /** 已授权人员固定投影。 @param tenant 可信租户UUID @param id 已授权父人员UUID @return 当前租户和父人员下的稳定投影 */
 List<Map<String,Object>> audit(String tenant,String id);
 /** 已授权人员固定投影。 @param tenant 可信租户UUID @param id 已授权父人员UUID @return 当前租户和父人员下的稳定投影 */
 List<Map<String,Object>> details(String tenant,String id);
 /** 已授权人员固定投影。 @param tenant 可信租户UUID @param id 已授权父人员UUID @return 当前租户和父人员下的稳定投影 */
 List<Map<String,Object>> assignments(String tenant,String id);
}
