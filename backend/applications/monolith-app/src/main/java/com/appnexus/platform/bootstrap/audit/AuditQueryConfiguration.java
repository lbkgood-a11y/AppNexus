package com.appnexus.platform.bootstrap.audit;
import com.appnexus.platform.audit.*;
import com.appnexus.platform.audit.port.AuditRecords;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.*;
import org.springframework.context.annotation.*;
/** 审计公开用例装配，不承担权限或查询规则。 */
@Configuration(proxyBeanMethods=false)
public class AuditQueryConfiguration {
 /** @param records 固定存储 @param platform 平台管理资格 @param access 功能授权 @param fields 实时统一字段保护 @param tenants 租户成员 @return 原统一审计查询 */
 @Bean public AuditQueries auditQueries(AuditRecords records,PlatformAdministration platform,AccessAuthorization access,TenantAccess tenants,com.appnexus.platform.authorization.FieldProtection fields){return AuditUseCases.queries(records,platform,access,tenants,fields);}
}
