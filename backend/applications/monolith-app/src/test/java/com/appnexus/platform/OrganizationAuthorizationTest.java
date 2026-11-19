package com.appnexus.platform;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import com.appnexus.platform.authorization.*;
import static org.assertj.core.api.Assertions.assertThat;
/** 确认授权默认拒绝，租户、成员、操作和有效期均参与授权判定。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
class OrganizationAuthorizationTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired OrganizationAuthorization authorization;
 @Test void organizationCapabilitiesDefaultDenyAndIgnorePostMigrationLegacyWrites() {
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),m=UUID.randomUUID(),other=UUID.randomUUID();
  assertThat(authorization.allows(a,m,OrganizationCapability.READ)).isFalse();
  jdbc.update("INSERT INTO organization_capability_grant(tenant_id,member_id,capability,granted_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'READ',UUID_TO_BIN(?))",a.toString(),m.toString(),UUID.randomUUID().toString());
  // V30 后旧表只用于历史对账，运行时不得因新增旧记录重新放行。
  assertThat(authorization.allows(a,m,OrganizationCapability.READ)).isFalse();
  assertThat(authorization.allows(a,m,OrganizationCapability.DELETE)).isFalse();
  assertThat(authorization.allows(b,m,OrganizationCapability.READ)).isFalse();
  assertThat(authorization.allows(a,other,OrganizationCapability.READ)).isFalse();
 }
 @Test void expiredOrRevokedGrantDenies() {
  UUID tenant=UUID.randomUUID(),member=UUID.randomUUID();
  jdbc.update("INSERT INTO organization_capability_grant(tenant_id,member_id,capability,granted_by,expires_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'CREATE',UUID_TO_BIN(?),CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND)",tenant.toString(),member.toString(),UUID.randomUUID().toString());
  assertThat(authorization.allows(tenant,member,OrganizationCapability.CREATE)).isFalse();
  jdbc.update("UPDATE organization_capability_grant SET expires_at=NULL,revoked_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
  assertThat(authorization.allows(tenant,member,OrganizationCapability.CREATE)).isFalse();
 }
}
