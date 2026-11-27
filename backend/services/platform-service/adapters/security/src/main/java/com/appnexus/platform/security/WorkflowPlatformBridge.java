package com.appnexus.platform.security;
import com.appnexus.platform.persistence.capability.CapabilityDirectoryMapper;

import com.appnexus.contracts.workflow.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.*;
import org.springframework.stereotype.Component;

/** 平台公开契约适配：复核租户成员、账号及统一权限，不授予单据数据权，也不读取工作流表。 */
@Component
public final class WorkflowPlatformBridge implements WorkflowPlatform {
  private final AccessAuthorization access;
  private final com.appnexus.platform.authorization.ScopedWriteGate writes;
  private final TenantAccess tenants;
  private final CapabilityDirectoryMapper mapper;
  private final org.springframework.beans.factory.ObjectProvider<com.appnexus.platform.authorization.FieldProtection> fields;
  private final WorkflowRoutingDirectory routing;

  /**
   * @param access 统一功能授权端口，逐次核验
   * @param writes 平台同事务执行许可
   * @param tenants 可信有效成员目录，禁止使用客户端成员标识
   * @param mapper 成员与账号最小目录的固定查询
   * @param routing 服务组合提供的旧流程路由扩展
   * @param fields 延迟统一字段保护，避免对象目录与平台桥接形成初始化循环；不可缺省放行
   */
  public WorkflowPlatformBridge(
      AccessAuthorization access, TenantAccess tenants, CapabilityDirectoryMapper mapper,WorkflowRoutingDirectory routing,org.springframework.beans.factory.ObjectProvider<com.appnexus.platform.authorization.FieldProtection> fields,com.appnexus.platform.authorization.ScopedWriteGate writes) {
    this.access = access;
    this.writes = writes;
    this.tenants = tenants;
    this.mapper = mapper;this.routing=routing;this.fields=fields;
  }
  /** 绑定可信租户及已通过业务约束的目标键，并复核原操作权限。 */
  @Override public void authorizeRecordWrite(WorkflowContext c,String permission,String... keys){
    writes.authorizeServiceOperation(UUID.fromString(c.tenantId()),permission,List.of(keys),()->allowed(c,permission));
  }
  /** 任务办理没有新增角色权限。应用已验证任务归属，桥接重查成员有效性并绑定实际账号。 */
  @Override public void authorizeTaskWrite(WorkflowContext c,String taskId){
    writes.authorizeServiceOperation(UUID.fromString(c.tenantId()),"workflow.task:complete",
        List.of(taskId,c.actorId()),()->active(c.tenantId(),c.actorId()));
  }

  /** 任务准入由工作流先验证，字段限制仍逐次消费统一权限策略，不能用审批身份绕过。 */
  @Override public Map<String,Object> projectData(WorkflowContext c,String resource,Map<String,Object> values){
    if(!active(c.tenantId(),c.actorId()))throw new SecurityException("WORKFLOW_FIELD_ACCESS_DENIED");
    var member=tenants.available(UUID.fromString(c.actorId())).stream().filter(m->m.tenantId().toString().equals(c.tenantId())).findFirst().orElseThrow(()->new SecurityException("WORKFLOW_FIELD_ACCESS_DENIED"));
    return fields.getObject().project(AccessAuthorization.Subject.tenant(UUID.fromString(c.actorId()),member.tenantId(),member.memberId()),resource,values);
  }

  /** 流程查看人员使用最小配置目录；所有查询强制当前租户且只含有效成员/组织/岗位。 */
  @Override
  public Map<String, List<WorkflowPlatform.Option>> configurationOptions(WorkflowContext c) {
    if (!allowed(c, "workflow:read")) throw new SecurityException("WORKFLOW_CONFIGURATION_DENIED");
    // 流程配置人员需要按名称选人；仅开放有效租户成员名称，不授予人员管理或原文读取权限。
    var options = new HashMap<>(routing.options(c.tenantId()));
    options.put("members", mapper.members(c.tenantId()));
    return Map.copyOf(options);
  }

  /** 当前申请人只能选择自身有效任职组织，组织名称不作为授权凭证。 */
  @Override
  public List<WorkflowPlatform.Option> documentOrganizations(WorkflowContext c) {
    if (!active(c.tenantId(), c.actorId())) throw new SecurityException("WORKFLOW_ORGANIZATION_DENIED");
    return tenants.available(UUID.fromString(c.actorId())).stream()
        .filter(member -> member.tenantId().toString().equals(c.tenantId()))
        .findFirst().map(member -> routing.documentOrganizations(c, member.memberId()))
        .orElseGet(List::of);
  }

  @Override
  public boolean active(String tenant, String actor) {
    try {
      return tenants.available(UUID.fromString(actor)).stream()
              .anyMatch(m -> m.tenantId().toString().equals(tenant))
          && Boolean.TRUE.equals(
              mapper.activeAccount(actor));
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

  /**
   * 校验办理账号在当前租户的候选资格。固定账号使用有效租户成员身份，
   * 岗位及负责人规则使用当前人员和业务组织，避免借用同账号其他人员的任职。
   *
   * @param c 可信的当前办理身份和租户上下文
   * @param organization 流程实例所属业务组织
   * @param kind 候选规则类型
   * @param reference 固定账号或岗位标识，负责人规则可省略
   * @return 当前账号是否满足候选规则
   */
  @Override
  public boolean candidate(WorkflowContext c, String organization, String kind, String reference) {
    if (!active(c.tenantId(), c.actorId())) return false;
    if ("MEMBER".equals(kind)) return c.actorId().equalsIgnoreCase(reference);
    return tenants.available(UUID.fromString(c.actorId())).stream()
        .filter(member -> member.tenantId().toString().equals(c.tenantId()))
        .anyMatch(member -> routing.candidate(c, member.memberId(), organization, kind, reference));
  }
  @Override
  public List<String> resolve(String tenant, String organization, String kind, String reference) {
    if (kind.equals("MEMBER")) return active(tenant, reference) ? List.of(reference) : List.of();
    return routing.resolve(tenant, organization, kind, reference).stream()
        .filter(account -> active(tenant, account)).toList();
  }
}
