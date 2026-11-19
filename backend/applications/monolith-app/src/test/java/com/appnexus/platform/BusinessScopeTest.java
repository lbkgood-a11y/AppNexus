package com.appnexus.platform;

import com.appnexus.platform.authorization.BusinessScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 业务范围契约测试；验证组合隔离，不依赖尚未实现的库存业务或模拟数据库授权。 */
class BusinessScopeTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID shanghai = UUID.randomUUID(), suzhou = UUID.randomUUID();
    private final UUID ownerA = UUID.randomUUID(), ownerB = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-09T00:00:00Z");
    private final BusinessScope.Definition definition =
            new BusinessScope.Definition("inventory.stock", Set.of("warehouse", "owner"));

    /** 两个合法分支不能产生仓库与货主的笛卡尔积，也不能借用读取范围执行修改。 */
    @Test void completeBranchesPreventCrossProductAndOperationMixing() {
        var branches = List.of(branch(tenant, "inventory.stock:read", shanghai, ownerA, null),
                branch(tenant, "inventory.stock:read", suzhou, ownerB, null));
        assertThat(allows(branches, shanghai, ownerA)).isTrue();
        assertThat(allows(branches, suzhou, ownerB)).isTrue();
        assertThat(allows(branches, shanghai, ownerB)).isFalse();
        assertThat(allows(branches, suzhou, ownerA)).isFalse();
        assertThat(BusinessScope.allows(definition, tenant, "inventory.stock:update", now,
                branches, Map.of("warehouse", shanghai, "owner", ownerA))).isFalse();
    }

    /** 外租户、到期瞬间及缺失维度均不得放行，多个授权期限独立判定。 */
    @Test void tenantExpiryAndIncompleteDimensionsFailClosed() {
        var expired = branch(tenant, "inventory.stock:read", shanghai, ownerA, now);
        var foreign = branch(UUID.randomUUID(), "inventory.stock:read", shanghai, ownerA, null);
        var active = branch(tenant, "inventory.stock:read", suzhou, ownerB, now.plusSeconds(1));
        var incomplete = new BusinessScope.Branch(UUID.randomUUID(), tenant, "inventory.stock:read",
                Map.of("warehouse", Set.of(shanghai)), null);
        assertThat(allows(List.of(expired, foreign, incomplete, active), shanghai, ownerA)).isFalse();
        assertThat(allows(List.of(expired, active), suzhou, ownerB)).isTrue();
        assertThat(BusinessScope.allows(definition, tenant, "inventory.stock:read", now,
                List.of(active), Map.of("warehouse", suzhou))).isFalse();
        assertThat(BusinessScope.resolve(definition, tenant, "purchase.order:read", now,
                List.of(active))).isEmpty();
    }

    /** 空范围不能解释为全部；调用方修改原集合不得改变已构造的授权。 */
    @Test void immutableValuesAndEmptyScopeValidation() {
        var owners = new java.util.HashSet<>(Set.of(ownerA));
        var grant = new BusinessScope.Branch(UUID.randomUUID(), tenant, "inventory.stock:read",
                Map.of("warehouse", Set.of(shanghai), "owner", owners), null);
        owners.add(ownerB);
        assertThat(allows(List.of(grant), shanghai, ownerB)).isFalse();
        assertThatThrownBy(() -> new BusinessScope.Branch(UUID.randomUUID(), tenant,
                "inventory.stock:read", Map.of("owner", Set.of()), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BusinessScope.Branch branch(UUID scopeTenant, String permission, UUID warehouse,
                                        UUID owner, Instant expires) {
        return new BusinessScope.Branch(UUID.randomUUID(), scopeTenant, permission,
                Map.of("warehouse", Set.of(warehouse), "owner", Set.of(owner)), expires);
    }

    private boolean allows(List<BusinessScope.Branch> branches, UUID warehouse, UUID owner) {
        return BusinessScope.allows(definition, tenant, "inventory.stock:read", now,
                branches, Map.of("warehouse", warehouse, "owner", owner));
    }
}
