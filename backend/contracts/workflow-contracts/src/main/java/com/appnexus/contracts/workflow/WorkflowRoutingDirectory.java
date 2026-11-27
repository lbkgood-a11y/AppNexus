package com.appnexus.contracts.workflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务组合层使用的旧流程路由扩展。实现负责将业务配置解析为账号，
 * 流程引擎通过 WorkflowPlatform 消费结果，平台外层继续校验账号和权限。
 */
public interface WorkflowRoutingDirectory {
  /** @param tenantId 已获流程查看权限的租户 @return 仅当前租户的非敏感配置选项 */
  Map<String, List<WorkflowPlatform.Option>> options(String tenantId);

  /**
   * @param context 已验证的实际账号与业务上下文
   * @param memberId 可信租户成员标识
   * @return 当前可选的单据归属目录
   */
  List<WorkflowPlatform.Option> documentOrganizations(WorkflowContext context, UUID memberId);

  /**
   * @param context 已验证的实际账号与业务上下文
   * @param memberId 可信租户成员标识
   * @param scopeId 单据存储的归属键
   * @param kind 已发布的路由类型
   * @param reference 已发布的路由参数
   * @return 当前请求是否符合路由条件
   */
  boolean candidate(WorkflowContext context, UUID memberId, String scopeId, String kind, String reference);

  /**
   * @param tenantId 可信租户标识
   * @param scopeId 单据存储的归属键
   * @param kind 已发布的路由类型
   * @param reference 已发布的路由参数
   * @return 待复核有效资格的账号标识
   */
  List<String> resolve(String tenantId, String scopeId, String kind, String reference);
}
