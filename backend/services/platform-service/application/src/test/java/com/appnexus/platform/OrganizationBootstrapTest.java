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
        assertThat(count("organization_capability_grant",tenant)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_effective_tenant_permission WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),member.toString())).isGreaterThan(0);
        jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
        bootstrap.initialize(tenant,member);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),member.toString())).isZero();
        assertThatThrownBy(() -> bootstrap.initialize(tenant,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsForeignMemberWithoutLeavingClaimOrGrants() {
        UUID tenant=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'invalid-bootstrap')",tenant.toString());
        assertThatThrownBy(() -> bootstrap.initialize(tenant,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(count("organization_administrator_bootstrap",tenant)).isZero();
        assertThat(count("organization_capability_grant",tenant)).isZero();
    }

    /** 系统角色写入失败必须连初始化标记一同回滚，修复故障后仍可重新初始化。 */
    @Test void failedRoleWriteRollsBackBootstrapClaim() {
        UUID tenant=UUID.randomUUID(),member=activeMember(tenant);
        jdbc.execute("ALTER TABLE access_role ADD CONSTRAINT test_bootstrap_role CHECK (tenant_id <> X'"+tenant.toString().replace("-","")+"')");
        try {
            assertThatThrownBy(() -> bootstrap.initialize(tenant,member)).isInstanceOf(RuntimeException.class);
            assertThat(count("organization_administrator_bootstrap",tenant)).isZero();
        } finally { jdbc.execute("ALTER TABLE access_role DROP CHECK test_bootstrap_role"); }
        bootstrap.initialize(tenant,member);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_effective_tenant_permission WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.toString())).isGreaterThan(0);
    }

    /** 已有统一管理权限不得通过初始化入口覆盖既有治理关系。 */
    @Test void rejectsExistingUnifiedManagementHistory() {
        UUID tenant=UUID.randomUUID(),member=activeMember(tenant);
        bootstrap.initialize(tenant,member);
        jdbc.update("DELETE FROM organization_administrator_bootstrap WHERE tenant_id=UUID_TO_BIN(?)",tenant.toString());
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
