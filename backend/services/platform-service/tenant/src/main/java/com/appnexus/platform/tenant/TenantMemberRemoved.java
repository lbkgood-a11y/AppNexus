package com.appnexus.platform.tenant;

import java.util.UUID;

/**
 * 成员退出事务内的同步通知，订阅者失败会回滚退出。
 * @param tenantId 可信租户标识
 * @param memberId 已退出成员标识
 * @param actorId 实际执行退出操作的账号
 */
public record TenantMemberRemoved(UUID tenantId, UUID memberId, UUID actorId) {}
