package com.appnexus.platform.authorization.port;
import com.appnexus.platform.authorization.PolicyTemplates;
import com.appnexus.platform.authorization.*;
import java.util.*;
/** 模板存储与固定快照编解码端口，不承担授权算法，仅平台持久化实现。 */
public interface PolicyTemplateStorage {
 /** @param id 新UUID @param owner 固定管理域UUID @param name 已校验名称 @param value 正式授权端口读取的快照 @param actor 可信账号 */
 void save(UUID id,UUID owner,String name,RolePermissionManagement.AuthorizationAssignment value,UUID actor);
 /** @param owner 当前管理域 @return 同域最近100条摘要 */
 List<PolicyTemplates.View> list(UUID owner);
 /** @param template 模板UUID @param owner 当前管理域 @return 唯一快照，不匹配必须拒绝 */
 RolePermissionManagement.AuthorizationAssignment load(UUID template,UUID owner);
 /** REQUIRED完整事务。 @param work 保存或应用模板的完整工作 @param <T> 返回类型 @return 原结果 */
 <T>T atomic(java.util.function.Supplier<T> work);
}
