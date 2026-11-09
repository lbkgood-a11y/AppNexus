package com.appnexus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.PermissionCatalog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 使用真实 MySQL 验证权限目录管理域隔离、稳定定义及阶段 A 默认拒绝边界。 */
@SpringBootTest
@ActiveProfiles("local")
class PermissionCatalogTest extends DatabaseTestSupport {
    @Autowired PermissionCatalog catalog;
    @Autowired AccessAuthorization authorization;
    @Autowired com.appnexus.platform.authorization.DataPolicyMetadataCatalog dataPolicyMetadata;

    @Test void listsPublishedPermissionsWithinRequestedScope() {
        var platform = catalog.list(PermissionCatalog.ScopeType.PLATFORM, false);
        var tenant = catalog.list(PermissionCatalog.ScopeType.TENANT, false);

        assertThat(platform).extracting(PermissionCatalog.Permission::code)
            .contains("identity.account:read", "access.role:grant", "configuration.menu:publish")
            .doesNotContain("organization.unit:read");
        assertThat(tenant).extracting(PermissionCatalog.Permission::code)
            .contains("access.tenant.role:grant", "organization.unit:read", "organization.permission:grant")
            .doesNotContain("configuration.menu:read");
        assertThat(platform).allSatisfy(permission -> {
            assertThat(permission.enabled()).isTrue();
            assertThat(permission.systemManaged()).isTrue();
            assertThat(permission.description()).isNotBlank();
        });
    }

    @Test void deniesUntilSubjectRoleRelationsExistAndFailsClosedForInvalidContext() {
        UUID account = UUID.randomUUID(), tenant = UUID.randomUUID(), member = UUID.randomUUID();

        assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(account), "identity.account:read")).isFalse();
        assertThat(authorization.hasPermission(AccessAuthorization.Subject.tenant(account, tenant, member), "organization.unit:read")).isFalse();
        assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(account), "organization.unit:read")).isFalse();
        assertThat(authorization.hasPermission(new AccessAuthorization.Subject(account, tenant, null), "organization.unit:read")).isFalse();
        assertThat(authorization.hasPermission(null, "identity.account:read")).isFalse();
        assertThat(authorization.hasPermission(AccessAuthorization.Subject.platform(account), "unknown:read")).isFalse();
    }

    @Test void exposesOnlyRegisteredStructuredDataPolicyMetadata() {
        var metadata=dataPolicyMetadata.list(PermissionCatalog.ScopeType.TENANT);
        assertThat(metadata).extracting(com.appnexus.platform.authorization.DataPolicyMetadataCatalog.ScopeSupport::permissionCode)
            .contains("organization.unit:read","organization.unit:create")
            .doesNotContain("organization.permission:grant");
        assertThat(metadata).allSatisfy(item->{assertThat(item.resourceType()).isEqualTo("organization.unit");assertThat(item.scopeType()).isNotEqualTo(com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.DENY);});
        assertThat(dataPolicyMetadata.list(PermissionCatalog.ScopeType.PLATFORM)).isEmpty();
    }
}
