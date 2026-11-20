package com.appnexus.platform.organization;

import java.util.Set;
import java.util.UUID;

/** 供平台授权执行器读取有效组织身份的公开端口；业务页面不得据此自行计算访问权。 */
public interface OrganizationMembership {
    /**
     * 返回账号成员关联的启用人员在本租户有效的组织归属，不回退历史成员主组织表。
     * @param tenantId 服务端可信租户 UUID
     * @param memberId 同租户成员 UUID
     * @return 启用组织集合；日期边界按数据库 UTC 当天且包含起止日，缺省日期无限制
     */
    Set<UUID> activeOrganizations(UUID tenantId,UUID memberId);
}
