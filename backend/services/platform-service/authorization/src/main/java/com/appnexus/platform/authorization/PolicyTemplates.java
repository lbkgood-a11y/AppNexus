package com.appnexus.platform.authorization;
import java.util.*;
/** 角色策略模板公开用例，所有应用仍通过正式授权保存执行器重验，模板不授予额外管理权。 */
public interface PolicyTemplates {
 /** 模板摘要，不返回完整授权快照。 */
 record View(UUID id,String name){}
 /** @param context 可信管理域 @param role 当前域源角色 @param name 1至100字符无控制字符 @return 新模板，不自动应用 */
 View save(RoleManagement.Context context,UUID role,String name);
 /** @param context 可信管理域 @param role 当前可管理角色 @return 最近100条同域模板 */
 List<View> list(RoleManagement.Context context,UUID role);
 /** @param context 可信管理域 @param role 当前目标角色 @param template 同域模板 @param version 目标角色当前CAS @param reason 1至500字符变更原因 @return 正式授权执行器保存结果 */
 RolePermissionManagement.AuthorizationAssignment apply(RoleManagement.Context context,UUID role,UUID template,long version,String reason);
 /** 原稳定业务拒绝，由API层映射HTTP。 */
 final class Rejection extends RuntimeException {public Rejection(){super("INVALID_POLICY_TEMPLATE");}}
}
