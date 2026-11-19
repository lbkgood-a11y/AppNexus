package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType;
import com.appnexus.platform.authorization.RowAccessControl;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 公共 MySQL 行范围编译器；仅生成固定谓词及绑定值，不查询业务表、不持有跨请求授权缓存。
 * <p>索引列保持原值比较，UUID 转换只作用于参数；租户边界必须由调用方始终独立强制。
 */
@Component
public final class MySqlRowAccessControl implements RowAccessControl {
    @Override
    public Predicate organizationPredicate(AccessPlan plan, String organizationColumn) {
        if (organizationColumn == null || !organizationColumn.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)?")) {
            throw new IllegalArgumentException("Organization column must be a fixed SQL identifier");
        }
        if (plan == null) return new Predicate(" AND 1=0", List.of());
        if (plan.rows().type() == ScopeType.ALL) return new Predicate("", List.of());
        if (plan.rows().type() != ScopeType.ORGANIZATION || plan.rows().organizationIds().isEmpty()) {
            return new Predicate(" AND 1=0", List.of());
        }
        var parameters = plan.rows().organizationIds().stream().sorted().map(UUID::toString).toList();
        return new Predicate(" AND " + organizationColumn + " IN ("
                + String.join(",", Collections.nCopies(parameters.size(), "UUID_TO_BIN(?)")) + ")", parameters);
    }

    @Override
    public boolean allowsOrganization(AccessPlan plan, UUID organizationId) {
        return plan != null && organizationId != null
                && (plan.rows().type() == ScopeType.ALL
                || plan.rows().type() == ScopeType.ORGANIZATION && plan.rows().organizationIds().contains(organizationId));
    }
}
