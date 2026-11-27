package com.appnexus.masterdata.adapter.persistence.organization;

import com.appnexus.contracts.workflow.WorkflowContext;
import com.appnexus.contracts.workflow.WorkflowPlatform.Option;
import com.appnexus.contracts.workflow.WorkflowRoutingDirectory;
import com.appnexus.masterdata.organization.PersonnelExecution;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 主数据对已发布组织和岗位路由提供兼容解析，工作流只接收账号结果。 */
@Component
public final class LegacyWorkflowRoutingAdapter implements WorkflowRoutingDirectory {
  private final PersonnelExecution personnel;
  private final LegacyWorkflowDirectoryMapper directory;

  /** @param personnel 人员身份与任职公共执行端口 @param directory 固定租户目录映射 */
  LegacyWorkflowRoutingAdapter(PersonnelExecution personnel, LegacyWorkflowDirectoryMapper directory) {
    this.personnel = personnel;
    this.directory = directory;
  }

  @Override
  public Map<String, List<Option>> options(String tenantId) {
    return Map.of("organizations", directory.organizations(tenantId), "positions", directory.positions(tenantId));
  }

  @Override
  public List<Option> documentOrganizations(WorkflowContext context, UUID memberId) {
    if (context.personId() == null || context.organizationId() == null) return List.of();
    return personnel.allowed(UUID.fromString(context.tenantId()), memberId,
        UUID.fromString(context.personId()), UUID.fromString(context.organizationId()))
        ? directory.organization(context.tenantId(), context.organizationId()) : List.of();
  }

  @Override
  public boolean candidate(WorkflowContext context, UUID memberId, String scopeId, String kind, String reference) {
    if (context.personId() == null || context.organizationId() == null
        || !context.organizationId().equalsIgnoreCase(scopeId)) return false;
    return personnel.routeCandidate(UUID.fromString(context.tenantId()), memberId,
        UUID.fromString(context.personId()), UUID.fromString(scopeId), kind, reference);
  }

  @Override
  public List<String> resolve(String tenantId, String scopeId, String kind, String reference) {
    return personnel.resolveAccounts(UUID.fromString(tenantId), UUID.fromString(scopeId), kind, reference);
  }
}
