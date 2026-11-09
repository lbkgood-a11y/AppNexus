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
  context(UUID.randomUUID(),tenant,UUID.randomUUID());
 }
 private void context(UUID account,UUID tenant,UUID member) {
  var request=new MockHttpServletRequest();
  request.setAttribute(VerifiedTenantContext.class.getName(),new VerifiedTenantContext(account,tenant,member,1));
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
 @org.springframework.beans.factory.annotation.Autowired com.appnexus.platform.organization.OrganizationStructure structure;
 @org.springframework.beans.factory.annotation.Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap organizationBootstrap;
 /** 类型树拒绝循环移动和使用中类型停用，证明核心生命周期约束在正式 MySQL 上生效。 */
 @Test void organizationTreePreservesTypeAndCycleInvariants() {
  context(UUID.randomUUID());
  var type=structure.createType("TEST_"+UUID.randomUUID().toString().substring(0,8),"测试类型");
  var root=structure.createUnit("ROOT_"+UUID.randomUUID().toString().substring(0,8),type.id(),null,"根组织");
  var child=structure.createUnit("CHILD_"+UUID.randomUUID().toString().substring(0,8),type.id(),root.id(),"子组织");
  assertThatThrownBy(()->structure.moveUnit(root.id(),child.id(),root.version())).isInstanceOf(IllegalArgumentException.class).hasMessage("ORGANIZATION_CYCLE");
  assertThatThrownBy(()->structure.updateType(type.id(),type.name(),false,type.version())).isInstanceOf(IllegalStateException.class).hasMessage("TYPE_IN_USE");
  RequestContextHolder.resetRequestAttributes();
 }
 /** 正式仓储端口在 A/B 租户相同 ID 下执行 CRUD，验证注入上下文覆盖业务参数。 */
 @Test void organizationRepositoryUsesVerifiedTenantForEveryOperation() {
  jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_organization_unit(tenant_id BINARY(16) NOT NULL,id BINARY(16) NOT NULL,name VARCHAR(100) NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(tenant_id,id))");
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),id=UUID.randomUUID();
  var adminA=authorizedMember(a);var adminB=authorizedMember(b);
  context(adminA[0],a,adminA[1]); units.create(new com.appnexus.platform.organization.OrganizationUnit(id,"部门甲"));
  context(adminB[0],b,adminB[1]); units.create(new com.appnexus.platform.organization.OrganizationUnit(id,"部门乙"));
  context(adminA[0],a,adminA[1]); assertThat(units.findAll()).containsExactly(new com.appnexus.platform.organization.OrganizationUnit(id,"部门甲"));
  assertThat(units.rename(id,"部门甲-改名")).isTrue();
  context(adminB[0],b,adminB[1]); assertThat(units.findAll()).containsExactly(new com.appnexus.platform.organization.OrganizationUnit(id,"部门乙"));
  assertThat(units.delete(id)).isTrue();
  context(adminA[0],a,adminA[1]); assertThat(units.findAll()).containsExactly(new com.appnexus.platform.organization.OrganizationUnit(id,"部门甲-改名"));
  RequestContextHolder.resetRequestAttributes();
 }
 private UUID[] authorizedMember(UUID tenant){
  UUID account=UUID.randomUUID(),member=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),?)",tenant.toString(),"隔离租户-"+tenant);
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",account.toString(),"isolation-"+account,"隔离管理员","unused");
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
  organizationBootstrap.initialize(tenant,member);return new UUID[]{account,member};
 }

}
