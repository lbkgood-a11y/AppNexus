package com.appnexus.platform.security;

import com.appnexus.contracts.printing.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

/** 打印平台公开桥接；每次调用复核有效租户成员和显式角色权限。 */
@Component
public final class PrintPlatformBridge implements PrintPlatform {
  private final AccessAuthorization access;
  private final TenantAccess tenants;
  private final IntegrationClients integrations;
  private final RegisteredClientRepository clients;
  public PrintPlatformBridge(AccessAuthorization access,TenantAccess tenants,IntegrationClients integrations,RegisteredClientRepository clients){this.access=access;this.tenants=tenants;this.integrations=integrations;this.clients=clients;}

  @Override public boolean allowed(PrintContext c,String permission){
    if(permission.equals("print.agent:operate")&&c.actorId().startsWith("partner-")) {
      var policy=integrations.policy(c.actorId());
      var client=clients.findById(c.actorId());
      return policy!=null&&policy.enabled()&&policy.tenant().toString().equals(c.tenantId())&&client!=null&&client.getScopes().contains("print-agent:operate");
    }
    try {
      UUID actor=UUID.fromString(c.actorId()),tenant=UUID.fromString(c.tenantId());
      return tenants.available(actor).stream().filter(m->m.tenantId().equals(tenant)).anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(actor,tenant,m.memberId()),permission));
    } catch(IllegalArgumentException e){return false;}
  }
}
