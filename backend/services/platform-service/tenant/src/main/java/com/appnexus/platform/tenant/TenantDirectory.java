package com.appnexus.platform.tenant;

import java.util.UUID;

/** 受控租户存在性输出端口；只供已授权平台配置用例核验目标，不返回租户业务明细。 */
public interface TenantDirectory {
  /**
   * 检查租户记录是否存在，与成员有效性和业务权限分别判定。
   * @param tenant 已由平台用例验证格式的目标租户 UUID
   * @return 是否存在；不隐式要求启用，保持原配置授权语义
   */
  boolean exists(UUID tenant);
}
