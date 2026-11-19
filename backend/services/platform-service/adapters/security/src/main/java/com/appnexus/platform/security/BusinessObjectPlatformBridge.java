package com.appnexus.platform.security;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 业务对象授权桥接；目录和租户配置使用各自专属权限，不授予业务原文访问权。 */
@Component
public final class BusinessObjectPlatformBridge implements BusinessObjectPlatform {
  private final PlatformAdministration platform;
  private final TenantAccess tenants;
  private final AccessAuthorization access;
  private final TenantDirectory directory;
  private final IntegrationClients integrations;
  private final org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;
  /** @param platform 平台管理公开端口 @param tenants 可信成员查询 @param access 实时授权 @param directory 受控租户存在性端口，不直接读数据库 @param integrations 现有合作方策略 @param clients 认证服务器权威客户端目录 */
  public BusinessObjectPlatformBridge(PlatformAdministration platform,TenantAccess tenants,AccessAuthorization access,TenantDirectory directory,IntegrationClients integrations,org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients){this.platform=platform;this.tenants=tenants;this.access=access;this.directory=directory;this.integrations=integrations;this.clients=clients;}
  /** 编码协助只发放字符串；必须具备目标操作权限，业务保存仍执行行范围和字段限制。 */
  @Override public boolean codingAllowed(BusinessObjects.Context c,String operationId,String permissionCode){
    return tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId()))
      .anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(UUID.fromString(c.actorId()),m.tenantId(),m.memberId()),permissionCode));
  }
  @Override public boolean tenantExists(String tenant){return directory.exists(UUID.fromString(tenant));}
  @Override public boolean platformAdministrator(String actor){return platform.isAdministrator(UUID.fromString(actor))&&access.hasPermission(AccessAuthorization.Subject.platform(UUID.fromString(actor)),"business.object.catalog:manage");}
  @Override public boolean active(BusinessObjects.Context c){
    if(c.actorId().startsWith("partner-")){var policy=integrations.policy(c.actorId());var client=clients.findById(c.actorId());return policy!=null&&policy.enabled()&&policy.tenant().toString().equals(c.tenantId())&&client!=null&&client.getScopes().contains("business-object:resolve");}
    return tenants.available(UUID.fromString(c.actorId())).stream().anyMatch(m->m.tenantId().toString().equals(c.tenantId()));
  }
  @Override public boolean tenantAdministrator(BusinessObjects.Context c){return tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(UUID.fromString(c.actorId()),m.tenantId(),m.memberId()),"business.object.configuration:manage"));}
}
