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
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final IntegrationClients integrations;
  private final org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;
  public BusinessObjectPlatformBridge(PlatformAdministration platform,TenantAccess tenants,AccessAuthorization access,org.springframework.jdbc.core.JdbcTemplate jdbc,IntegrationClients integrations,org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients){this.platform=platform;this.tenants=tenants;this.access=access;this.jdbc=jdbc;this.integrations=integrations;this.clients=clients;}
  @Override public boolean tenantExists(String tenant){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_tenant WHERE id=UUID_TO_BIN(?))",Boolean.class,tenant));}
  @Override public boolean platformAdministrator(String actor){return platform.isAdministrator(UUID.fromString(actor))&&access.hasPermission(AccessAuthorization.Subject.platform(UUID.fromString(actor)),"business.object.catalog:manage");}
  @Override public boolean active(BusinessObjects.Context c){
    if(c.actorId().startsWith("partner-")){var policy=integrations.policy(c.actorId());var client=clients.findById(c.actorId());return policy!=null&&policy.enabled()&&policy.tenant().toString().equals(c.tenantId())&&client!=null&&client.getScopes().contains("business-object:resolve");}
    return tenants.available(UUID.fromString(c.actorId())).stream().anyMatch(m->m.tenantId().toString().equals(c.tenantId()));
  }
  @Override public boolean tenantAdministrator(BusinessObjects.Context c){return tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(UUID.fromString(c.actorId()),m.tenantId(),m.memberId()),"business.object.configuration:manage"));}
}
