package com.appnexus.platform.tenant;

import java.util.UUID;

/**
 * 租户开通事务内的同步通知；订阅者失败导致开通回滚，不允许异步补造初始化成功。
 * @param tenantId 已在当前事务创建的可信租户 UUID
 * @param actorId 经过认证的开通账号 UUID，不是被授予管理员的目标账号
 */
public record TenantProvisioned(UUID tenantId,UUID actorId) {}
