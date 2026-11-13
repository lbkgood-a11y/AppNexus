package com.appnexus.platform.security;

import com.appnexus.contracts.document.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 单据授权桥接；租户管理能力复用现有 organization.permission:grant，不另建配置资格体系。 */
@Component
public final class DocumentPlatformBridge implements DocumentPlatform {
  private final PlatformAdministration platform;
  private final TenantAccess tenants;
  private final AccessAuthorization access;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final IntegrationClients integrations;
  private final org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;
  public DocumentPlatformBridge(PlatformAdministration platform,TenantAccess tenants,AccessAuthorization access,org.springframework.jdbc.core.JdbcTemplate jdbc,IntegrationClients integrations,org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients){this.platform=platform;this.tenants=tenants;this.access=access;this.jdbc=jdbc;this.integrations=integrations;this.clients=clients;}
  @Override public boolean tenantExists(String tenant){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM platform_tenant WHERE id=UUID_TO_BIN(?))",Boolean.class,tenant));}
  @Override public boolean platformAdministrator(String actor){return platform.isAdministrator(UUID.fromString(actor));}
  @Override public boolean active(Documents.Context c){
    if(c.actorId().startsWith("partner-")){var policy=integrations.policy(c.actorId());var client=clients.findById(c.actorId());return policy!=null&&policy.enabled()&&policy.tenant().toString().equals(c.tenantId())&&client!=null&&client.getScopes().contains("document:resolve");}
    return tenants.available(UUID.fromString(c.actorId())).stream().anyMatch(m->m.tenantId().toString().equals(c.tenantId()));
  }
  @Override public boolean tenantAdministrator(Documents.Context c){return tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(UUID.fromString(c.actorId()),m.tenantId(),m.memberId()),"organization.permission:grant"));}
}
