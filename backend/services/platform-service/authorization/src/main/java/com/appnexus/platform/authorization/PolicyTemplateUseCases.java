package com.appnexus.platform.authorization;
import com.appnexus.platform.authorization.port.PolicyTemplateStorage;
/** 模板纯用例无框架装配入口。 */
public final class PolicyTemplateUseCases {
 private PolicyTemplateUseCases(){}
 /** @param roles 正式统一授权保存端口 @param catalog 当前权威数据权限目录 @param storage 固定存储与事务 @return 模板公开用例 */
 public static PolicyTemplates create(RolePermissionManagement roles,DataAccessCatalog catalog,PolicyTemplateStorage storage){return new com.appnexus.platform.authorization.internal.application.template.PolicyTemplateService(roles,catalog,storage);}
}
