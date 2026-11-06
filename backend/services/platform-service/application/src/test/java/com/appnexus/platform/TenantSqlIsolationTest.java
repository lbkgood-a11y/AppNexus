package com.appnexus.platform;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import com.appnexus.platform.persistence.TenantScoped;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 验证同业务 ID 在 A/B 租户隔离，测试表不进入生产迁移。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
class TenantSqlIsolationTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired SqlSessionFactory factory;
 interface UnsafeMapper { @Select("SELECT id FROM identity_account WHERE id=UUID_TO_BIN('00000000-0000-0000-0000-000000000001')") Map<String,Object> read(); }
 @com.appnexus.platform.persistence.PlatformScoped(tables={"account_security_log"})
 interface PlatformMapper {
  @Select("SELECT id FROM account_security_log LIMIT 1") List<Long> allowed();
  @Select("SELECT l.id FROM account_security_log l JOIN identity_account a ON a.id=l.account_id LIMIT 1") int outside();
 }
 @TenantScoped(table="tenant_sql_fixture")
 interface FixtureMapper {
  @Insert("INSERT INTO tenant_sql_fixture (tenant_id, id, value) VALUES (#{__verifiedTenant}, #{id}, #{value})")
  int insert(Map<String,Object> values);
  @Select("SELECT id, value FROM tenant_sql_fixture WHERE tenant_id = #{__verifiedTenant} AND id = #{id}")
  Map<String,Object> find(Map<String,Object> values);
  @Update("UPDATE tenant_sql_fixture SET value = #{value} WHERE tenant_id = #{__verifiedTenant} AND id = #{id}")
  int update(Map<String,Object> values);
  @Delete("DELETE FROM tenant_sql_fixture WHERE tenant_id = #{__verifiedTenant} AND id = #{id}")
  int delete(Map<String,Object> values);
  @Select("SELECT id, value FROM tenant_sql_fixture WHERE tenant_id = #{__verifiedTenant} OR id = #{id}")
  Map<String,Object> unsafe(Map<String,Object> values);
 }
 /** 仅测试显式建立请求上下文，正式应用只有安全过滤器可创建该属性。 */
 private void context(UUID tenant) {
  var request=new MockHttpServletRequest();
  request.setAttribute(VerifiedTenantContext.class.getName(),new VerifiedTenantContext(UUID.randomUUID(),tenant,UUID.randomUUID(),1));
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
 }
 @Test void undeclaredMappersFailClosed() {
  if(!factory.getConfiguration().hasMapper(UnsafeMapper.class)) factory.getConfiguration().addMapper(UnsafeMapper.class);
  try(var session=factory.openSession(true)) {
   assertThatThrownBy(()->session.getMapper(UnsafeMapper.class).read())
     .hasRootCauseMessage("Mapper scope declaration required");
  }
 }

 @Test void platformMapperTableAccessIsRestrictedByAllowlist() {
  if(!factory.getConfiguration().hasMapper(PlatformMapper.class)) factory.getConfiguration().addMapper(PlatformMapper.class);
  try(var session=factory.openSession(true)) {
   assertThat(session.getMapper(PlatformMapper.class).allowed()).isNotNull();
   assertThatThrownBy(()->session.getMapper(PlatformMapper.class).outside())
    .hasStackTraceContaining("Platform SQL table outside allowlist");
  }
 }
 @Test void crudCannotEscapeVerifiedTenant() {
  jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_sql_fixture(tenant_id BINARY(16) NOT NULL,id INT NOT NULL,value VARCHAR(100),PRIMARY KEY(tenant_id,id))");
  if(!factory.getConfiguration().hasMapper(FixtureMapper.class)) factory.getConfiguration().addMapper(FixtureMapper.class);
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var parameters=new HashMap<String,Object>();parameters.put("id",1);parameters.put("value","A");parameters.put("__verifiedTenant",new byte[16]);
  try(var session=factory.openSession(true)) {
   var mapper=session.getMapper(FixtureMapper.class);
   assertThatThrownBy(()->mapper.find(parameters)).hasStackTraceContaining("Verified tenant context required");
   context(a);mapper.insert(parameters);
   context(b);parameters.put("value","B");mapper.insert(parameters);
   context(a);session.clearCache();assertThat(mapper.find(parameters).get("value")).isEqualTo("A");
   parameters.put("value","A-updated");assertThat(mapper.update(parameters)).isEqualTo(1);
   assertThatThrownBy(()->mapper.unsafe(parameters)).hasStackTraceContaining("Unsupported tenant SQL shape");
   context(b);session.clearCache();assertThat(mapper.find(parameters).get("value")).isEqualTo("B");
   mapper.delete(parameters);
   context(a);session.clearCache();assertThat(mapper.find(parameters).get("value")).isEqualTo("A-updated");
  } finally {RequestContextHolder.resetRequestAttributes();}
 }
 @org.springframework.beans.factory.annotation.Autowired com.appnexus.platform.organization.OrganizationUnitRepository units;
 /** 正式仓储端口在 A/B 租户相同 ID 下执行 CRUD，验证注入上下文覆盖业务参数。 */
 @Test void organizationRepositoryUsesVerifiedTenantForEveryOperation() {
  jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_organization_unit(tenant_id BINARY(16) NOT NULL,id BINARY(16) NOT NULL,name VARCHAR(100) NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(tenant_id,id))");
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),id=UUID.randomUUID();
  context(a); units.create(new com.appnexus.platform.organization.OrganizationUnit(id,"部门甲"));
  context(b); units.create(new com.appnexus.platform.organization.OrganizationUnit(id,"部门乙"));
  context(a); assertThat(units.findAll()).containsExactly(new com.appnexus.platform.organization.OrganizationUnit(id,"部门甲"));
  assertThat(units.rename(id,"部门甲-改名")).isTrue();
  context(b); assertThat(units.findAll()).containsExactly(new com.appnexus.platform.organization.OrganizationUnit(id,"部门乙"));
  assertThat(units.delete(id)).isTrue();
  context(a); assertThat(units.findAll()).containsExactly(new com.appnexus.platform.organization.OrganizationUnit(id,"部门甲-改名"));
  RequestContextHolder.resetRequestAttributes();
 }

}
