package com.appnexus.platform.authorization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 旧成员直授权与统一授权快照的只读对账端口；对账不修改或删除生产授权数据。
 */
public interface LegacyAuthorizationReconciliation {
    /**
     * @param actor 已认证的平台管理员账号 UUID
     * @param page 从 1 开始的页码
     * @param size 每页 1 至 100 条，避免无界导出授权目录
     * @return 当前仍有效的旧授权及其统一 RBAC 覆盖状态
     */
    Report report(UUID actor, int page, int size);

    /** 单条旧授权对账结果；coveredByUnified=false 时关闭双读将导致减权。 */
    record Difference(UUID tenantId, UUID memberId, String permissionCode, Instant expiresAt,
                      boolean coveredByUnified) {}

    /** total 是有效旧授权总数，uncovered 是尚未被统一授权覆盖的总数。 */
    record Report(List<Difference> items, long total, long uncovered, int page, int size) {}
}
