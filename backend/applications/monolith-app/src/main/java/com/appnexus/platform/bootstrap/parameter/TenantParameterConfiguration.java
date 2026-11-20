package com.appnexus.platform.bootstrap.parameter;
import com.appnexus.platform.configuration.*;
import com.appnexus.platform.configuration.port.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.TenantAccess;
import org.springframework.context.annotation.*;
/** 参数用例装配，不承担参数规则或授权决策。 */
@Configuration(proxyBeanMethods=false)
public class TenantParameterConfiguration {
 /** @param repository 固定存储端口 @param access 权威功能授权 @param tenants 权威成员状态 @param transaction 完整参数事务 @return 非敏感参数公开用例 */
 @Bean public TenantParameters tenantParameters(TenantParameterRepository repository,AccessAuthorization access,TenantAccess tenants,TenantParameterTransaction transaction){return TenantParameterUseCases.create(repository,access,tenants,transaction);}
}
