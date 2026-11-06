package com.appnexus.platform;

import com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 验证一次性配置、撤权后不恢复、无效目标拒绝及审计原子性。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
class OrganizationBootstrapTest extends DatabaseTestSupport {
    @Autowired OrganizationAdministratorBootstrap bootstrap;
    @Autowired JdbcTemplate jdbc;

    @Test void initializesOnceAndNeverRestoresRevokedPermissions() {
        UUID tenant=UUID.randomUUID(), member=UUID.randomUUID(), account=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'bootstrap','unused')",account.toString(),"bootstrap-"+account);
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'bootstrap-test')",tenant.toString());
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
        bootstrap.initialize(tenant,member);
        bootstrap.initialize(tenant,member);
        assertThat(count("organization_capability_grant",tenant)).isEqualTo(5);
        assertThat(count("organization_grant_audit",tenant)).isEqualTo(5);
        jdbc.update("UPDATE organization_capability_grant SET revoked_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?)",tenant.toString());
        bootstrap.initialize(tenant,member);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(?) AND revoked_at IS NULL",Integer.class,tenant.toString())).isZero();
        assertThatThrownBy(() -> bootstrap.initialize(tenant,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsForeignMemberWithoutLeavingClaimOrGrants() {
        UUID tenant=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'invalid-bootstrap')",tenant.toString());
        assertThatThrownBy(() -> bootstrap.initialize(tenant,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(count("organization_administrator_bootstrap",tenant)).isZero();
        assertThat(count("organization_capability_grant",tenant)).isZero();
    }

    /** 审计失败必须连初始化标记一同回滚，修复故障后仍可重新初始化。 */
    @Test void failedAuditRollsBackClaimAndAllCapabilities() {
        UUID tenant=UUID.randomUUID(),member=activeMember(tenant);
        jdbc.execute("ALTER TABLE organization_grant_audit ADD CONSTRAINT test_bootstrap_audit CHECK (actor_member_id <> X'"+member.toString().replace("-","")+"')");
        try {
            assertThatThrownBy(() -> bootstrap.initialize(tenant,member)).isInstanceOf(RuntimeException.class);
            assertThat(count("organization_administrator_bootstrap",tenant)).isZero();
            assertThat(count("organization_capability_grant",tenant)).isZero();
            assertThat(count("organization_grant_audit",tenant)).isZero();
        } finally { jdbc.execute("ALTER TABLE organization_grant_audit DROP CHECK test_bootstrap_audit"); }
        bootstrap.initialize(tenant,member);
        assertThat(count("organization_capability_grant",tenant)).isEqualTo(5);
    }

    /** 已有人工管理权限即使已撤销，也不得通过初始化入口恢复或替换。 */
    @Test void rejectsExistingManagementHistory() {
        UUID tenant=UUID.randomUUID(),member=activeMember(tenant);
        jdbc.update("INSERT INTO organization_capability_grant(tenant_id,member_id,capability,granted_by,revoked_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'GRANT',UUID_TO_BIN(?),CURRENT_TIMESTAMP(6))",tenant.toString(),member.toString(),member.toString());
        assertThatThrownBy(() -> bootstrap.initialize(tenant,member)).isInstanceOf(IllegalStateException.class);
        assertThat(count("organization_administrator_bootstrap",tenant)).isZero();
    }

    /** 仅在独立测试数据库建立有效账号和成员，测试结束随容器销毁。 */
    private UUID activeMember(UUID tenant) {
        UUID account=UUID.randomUUID(),member=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'bootstrap-test')",tenant.toString());
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'bootstrap','unused')",account.toString(),"bootstrap-"+account);
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
        return member;
    }

    /** 测试表名仅来自固定调用点，不接受外部输入。 */
    private int count(String table,UUID tenant) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.toString());
    }
}
