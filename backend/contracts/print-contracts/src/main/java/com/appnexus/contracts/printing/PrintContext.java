package com.appnexus.contracts.printing;

import java.util.Objects;

/**
 * 打印调用的显式身份上下文；仅传递宿主认证链建立的声明，本身不是凭证。
 * @param tenantId 当前租户 UUID，必填、无默认值
 * @param actorId 实际操作者账号 UUID；代理入口为经认证的机密客户端 ID，必填、无默认值
 * @param requestId 写请求幂等 UUID；读请求由服务端生成，不接受其改变租户或身份
 */
public record PrintContext(String tenantId, String actorId, String requestId) {
  public PrintContext {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(requestId, "requestId");
    if (tenantId.isBlank() || actorId.isBlank() || requestId.isBlank()) {
      throw new IllegalArgumentException("print context identifiers must not be blank");
    }
  }
}
