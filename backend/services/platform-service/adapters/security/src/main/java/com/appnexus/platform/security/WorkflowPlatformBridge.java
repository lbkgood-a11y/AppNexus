package com.appnexus.platform.security;

import com.appnexus.contracts.workflow.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 平台公开契约适配：组织查询显式带租户与有效期，不授予单据数据权，也不读取工作流表。 */
@Component
public final class WorkflowPlatformBridge implements WorkflowPlatform {
  private final AccessAuthorization access;
  private final TenantAccess tenants;
  private final JdbcTemplate jdbc;
  private final com.appnexus.platform.organization.PersonnelExecution personnel;

  public WorkflowPlatformBridge(
      AccessAuthorization access, TenantAccess tenants, JdbcTemplate jdbc,com.appnexus.platform.organization.PersonnelExecution personnel) {
    this.access = access;
    this.tenants = tenants;
    this.jdbc = jdbc;this.personnel=personnel;
  }

  /** 流程查看人员使用最小配置目录；所有查询强制当前租户且只含有效成员/组织/岗位。 */
  @Override
  public Map<String, List<WorkflowPlatform.Option>> configurationOptions(WorkflowContext c) {
    if (!allowed(c, "workflow:read")) throw new SecurityException("WORKFLOW_CONFIGURATION_DENIED");
    // 流程配置人员需要按名称选人；仅开放有效租户成员名称，不授予人员管理或原文读取权限。
    var members = jdbc.query("SELECT BIN_TO_UUID(a.id) id,a.display_name name FROM tenant_member m"
        + " JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(?)"
        + " AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE ORDER BY a.display_name,a.id",
        (r, n) -> new WorkflowPlatform.Option(r.getString("id"), r.getString("name")), c.tenantId());
    var organizations = jdbc.query("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit"
        + " WHERE tenant_id=UUID_TO_BIN(?) AND enabled=TRUE ORDER BY name,id",
        (r, n) -> new WorkflowPlatform.Option(r.getString("id"), r.getString("name")), c.tenantId());
    var positions = jdbc.query("SELECT BIN_TO_UUID(p.id) id,CONCAT(u.name,' / ',p.name) name"
        + " FROM tenant_organization_position p JOIN tenant_organization_unit u ON u.tenant_id=p.tenant_id"
        + " AND u.id=p.organization_id WHERE p.tenant_id=UUID_TO_BIN(?) AND p.enabled=TRUE AND u.enabled=TRUE ORDER BY u.name,p.name,p.id",
        (r, n) -> new WorkflowPlatform.Option(r.getString("id"), r.getString("name")), c.tenantId());
    return Map.of("members", members, "organizations", organizations, "positions", positions);
  }

  /** 当前申请人只能选择自身有效任职组织，组织名称不作为授权凭证。 */
  @Override
  public List<WorkflowPlatform.Option> documentOrganizations(WorkflowContext c) {
    if (!active(c.tenantId(), c.actorId())) throw new SecurityException("WORKFLOW_ORGANIZATION_DENIED");
    // 只消费已选择人员的组织，禁止合并同账号关联的其他人员身份。
    if(c.personId()==null||c.organizationId()==null)return List.of();
    var memberships=tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).toList();
    if(memberships.isEmpty()||!personnel.allowed(UUID.fromString(c.tenantId()),memberships.getFirst().memberId(),UUID.fromString(c.personId()),UUID.fromString(c.organizationId())))return List.of();
    return jdbc.query("SELECT BIN_TO_UUID(id) id,name FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",(rs,n)->new WorkflowPlatform.Option(rs.getString("id"),rs.getString("name")),c.tenantId(),c.organizationId());
  }

  @Override
  public boolean active(String tenant, String actor) {
    try {
      return tenants.available(UUID.fromString(actor)).stream()
              .anyMatch(m -> m.tenantId().toString().equals(tenant))
          && Boolean.TRUE.equals(
              jdbc.queryForObject(
                  "SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN(?)",
                  Boolean.class,
                  actor));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public boolean allowed(WorkflowContext c, String permission) {
    if (!active(c.tenantId(), c.actorId())) return false;
    return tenants.available(UUID.fromString(c.actorId())).stream()
        .filter(m -> m.tenantId().toString().equals(c.tenantId()))
        .anyMatch(
            m ->
                access.hasPermission(
                    AccessAuthorization.Subject.tenant(
                        UUID.fromString(c.actorId()), m.tenantId(), m.memberId()),
                    permission));
  }

  /** 岗位及负责人按当前人员核验，不把同账号所有人员的任职合并；固定账号节点保持账号语义。 */
  @Override public boolean candidate(WorkflowContext c,String organization,String kind,String reference){
    if(c.personId()==null||c.organizationId()==null||!active(c.tenantId(),c.actorId()))return false;
    var member=tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).findFirst();if(member.isEmpty())return false;
    if(kind.equals("MEMBER"))return c.actorId().equalsIgnoreCase(reference)&&personnel.allowed(UUID.fromString(c.tenantId()),member.get().memberId(),UUID.fromString(c.personId()),UUID.fromString(c.organizationId()));
    if(!c.organizationId().equalsIgnoreCase(organization))return false;
    return personnel.routeCandidate(UUID.fromString(c.tenantId()),member.get().memberId(),UUID.fromString(c.personId()),UUID.fromString(organization),kind,reference);
  }
  @Override
  public List<String> resolve(String tenant, String organization, String kind, String reference) {
    if (kind.equals("MEMBER")) return active(tenant, reference) ? List.of(reference) : List.of();
    UUID.fromString(tenant);
    UUID.fromString(organization);
    return personnel.resolveAccounts(UUID.fromString(tenant),UUID.fromString(organization),kind,reference).stream().filter(a->active(tenant,a)).toList();
  }
}
