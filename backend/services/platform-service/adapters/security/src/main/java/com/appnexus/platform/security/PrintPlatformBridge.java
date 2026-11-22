package com.appnexus.platform.security;
import com.appnexus.platform.persistence.capability.CapabilityDirectoryMapper;

import com.appnexus.contracts.printing.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

/** 打印平台公开桥接；每次调用复核有效租户成员和显式角色权限。 */
@Component
public final class PrintPlatformBridge implements PrintPlatform {
  private final CapabilityDirectoryMapper mapper;
  private final AccessAuthorization access;
  private final TenantAccess tenants;
  private final IntegrationClients integrations;
  private final RegisteredClientRepository clients;
  private final com.appnexus.platform.authorization.ScopedWriteGate writes;
  public PrintPlatformBridge(AccessAuthorization access,TenantAccess tenants,IntegrationClients integrations,RegisteredClientRepository clients,CapabilityDirectoryMapper mapper,com.appnexus.platform.authorization.ScopedWriteGate writes){this.access=access;this.tenants=tenants;this.integrations=integrations;this.clients=clients;this.mapper=mapper;this.writes=writes;}
  @Override public java.util.Map<String,String> agentIdentity(PrintContext c){
    if(!allowed(c,"print.agent:operate"))throw new SecurityException("PRINT_ACCESS_DENIED");
    String name=mapper.tenantName(c.tenantId());
    return java.util.Map.of("tenantId",c.tenantId(),"tenantName",name,"clientId",c.actorId());
  }

  /** 使用现行代理准入，原认证主体和同一站点实例随许可锁定。 */
  @Override public void authorizeAgentQueues(PrintContext c,String site,String agent){
    try{
      writes.authorizeServiceOperation(UUID.fromString(c.tenantId()),"print.agent:operate",
          java.util.List.of(c.actorId(),site,agent),()->allowed(c,"print.agent:operate"));
    }catch(SecurityException denied){
      throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    }
  }

  /** 在同一事务重查原操作权限，并将许可限定为可信租户的指定打印记录。 */
  @Override public void authorizeRecordWrite(PrintContext c,String permission,String... targets){
    try{
      writes.authorizeServiceOperation(UUID.fromString(c.tenantId()),permission,
          java.util.List.of(targets),()->allowed(c,permission));
    }catch(SecurityException denied){
      throw new PrintRejection("PRINT_ACCESS_DENIED",403);
    }
  }

  @Override public boolean allowed(PrintContext c,String permission){
    if(c.actorId().startsWith("partner-")) {
      var policy=integrations.policy(c.actorId());
      var client=clients.findById(c.actorId());
      return policy!=null&&policy.enabled()&&policy.tenant().toString().equals(c.tenantId())&&client!=null&&(permission.equals("print.agent:operate")&&client.getScopes().contains("print-agent:operate")||java.util.Set.of("print.external:submit","print.job:submit","print.job:read").contains(permission)&&client.getScopes().contains("print:submit"));
    }
    try {
      UUID actor=UUID.fromString(c.actorId()),tenant=UUID.fromString(c.tenantId());
      return tenants.available(actor).stream().filter(m->m.tenantId().equals(tenant)).anyMatch(m->access.hasPermission(AccessAuthorization.Subject.tenant(actor,tenant,m.memberId()),permission));
    } catch(IllegalArgumentException e){return false;}
  }
}
