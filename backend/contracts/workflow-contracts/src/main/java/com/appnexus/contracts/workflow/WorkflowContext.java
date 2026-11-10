package com.appnexus.contracts.workflow;

import java.util.Objects;

/**
 * 工作流调用的显式身份上下文，避免组合部署时依赖请求线程中的隐式租户信息。 本对象仅传递调用声明，不证明身份可信；接收入口仍须校验会话、成员资格及权限。
 *
 * @param tenantId 租户唯一标识，必填；不得用于跨租户访问
 * @param actorId 实际操作者账号标识，必填；不得由浏览器声明后直接信任
 * @param requestId 请求幂等标识，必填；未来写入口须结合租户和操作类型持久化去重
 */
public record WorkflowContext(String tenantId, String actorId, String requestId) {
  /**
   * 校验上下文完整性，不执行身份认证或幂等落库。
   *
   * @throws IllegalArgumentException 任一标识为空白时抛出
   * @throws NullPointerException 任一标识为 null 时抛出
   */
  public WorkflowContext {
    requireIdentifier(tenantId, "tenantId");
    requireIdentifier(actorId, "actorId");
    requireIdentifier(requestId, "requestId");
  }

  private static void requireIdentifier(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
