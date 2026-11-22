package com.appnexus.platform.configuration;

import java.util.*;

/** 主数据公开地理目录端口；调用方须先复核自身业务读取权限，不能由客户端指定租户。 */
public interface GeographyCatalog {
  /** 租户业务节点摘要；父级空表示国家根节点，历史停用节点仅供回显。 */
  record Node(String code,String name,String countryCode,String parentCode,boolean enabled) {}
  /**
   * @param tenantId 可信租户 UUID；无默认值，不读取其他租户或标准参考快照
   * @return 完整目录摘要，不使用普通列表分页截断
   */
  List<Node> nodes(UUID tenantId);
  /** @param tenantId 可信租户 @param codes 去重的地理编码，单批最多 200 个
   * @return 当前租户地理名称以及标准洲名称 */
  Map<String,String> names(UUID tenantId,List<String> codes);
}
