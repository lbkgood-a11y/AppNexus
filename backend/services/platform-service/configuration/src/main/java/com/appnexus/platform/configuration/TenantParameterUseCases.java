package com.appnexus.platform.configuration;
import com.appnexus.platform.configuration.port.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.TenantAccess;
/** 无框架参数用例装配工厂，外部不导入internal实现。 */
public final class TenantParameterUseCases {
 private TenantParameterUseCases(){}
 /** @param repository 固定存储 @param access 权威授权 @param tenants 权威租户状态 @param transaction 原完整事务 @return 参数公开用例 */
 public static TenantParameters create(TenantParameterRepository repository,AccessAuthorization access,TenantAccess tenants,TenantParameterTransaction transaction){return new com.appnexus.platform.configuration.internal.application.parameter.TenantParameterService(repository,access,tenants,transaction);}
}
