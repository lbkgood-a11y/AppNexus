package com.appnexus.platform.audit;
import com.appnexus.platform.audit.port.AuditRecords;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.*;
/** 无框架审计用例装配工厂，外部不导入internal实现。 */
public final class AuditUseCases {
 private AuditUseCases(){}
 /** @param records 固定审计存储 @param platform 权威平台管理资格 @param access 统一功能授权 @param fields 实时统一字段保护 @param tenants 权威租户成员 @return 审计公开查询 */
 public static AuditQueries queries(AuditRecords records,PlatformAdministration platform,AccessAuthorization access,TenantAccess tenants,com.appnexus.platform.authorization.FieldProtection fields){return new com.appnexus.platform.audit.internal.application.query.AuditQueryService(records,platform,access,tenants,fields);}
}
