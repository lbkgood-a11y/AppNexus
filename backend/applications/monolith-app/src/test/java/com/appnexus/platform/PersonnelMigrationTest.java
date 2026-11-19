package com.appnexus.platform;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
/** 真实 MySQL 从 V76 有数据升级，验证原用户组织/岗位迁移到独立人员，不改变登录账号或权限。 */
class PersonnelMigrationTest {
 @Test void migratesLegacyRelationshipsAndPreservesAccount(){
  try(var mysql=new MySQLContainer("mysql:8.4")){
   mysql.start();var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var jdbc=new JdbcTemplate(source);
   Flyway.configure().dataSource(source).locations("classpath:db/migration").target("76").load().migrate();
   String tenant=UUID.randomUUID().toString(),account=UUID.randomUUID().toString(),member=UUID.randomUUID().toString(),type=UUID.randomUUID().toString(),org=UUID.randomUUID().toString(),pos=UUID.randomUUID().toString(),assignment=UUID.randomUUID().toString();
   jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'迁移测试')",tenant);
   // V76 仓库尚无记录审计；升级不得把迁移时刻或迁移账号伪装为原创建事实。
   String warehouse=UUID.randomUUID().toString();
   jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'LEGACY','历史仓库')",warehouse,tenant);
   jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),'migration-test','原真实用户','unused')",account);
   jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,account);
   jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务')",tenant,type);
   jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'O1',UUID_TO_BIN(?),'组织')",tenant,org,type);
   jdbc.update("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'P1','岗位')",tenant,pos,org);
   jdbc.update("INSERT INTO tenant_member_primary_organization(tenant_id,member_id,organization_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",tenant,member,org);
   jdbc.update("INSERT INTO tenant_organization_assignment(tenant_id,id,member_id,position_id,starts_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'2020-01-01')",tenant,assignment,member,pos);
   jdbc.update("INSERT INTO tenant_organization_leader(tenant_id,organization_id,assignment_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",tenant,org,assignment);
   Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
   var legacyAudit=jdbc.queryForMap("SELECT created_by,created_at,updated_by,updated_at FROM md_warehouse WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant,warehouse);
   assertThat(legacyAudit.values()).containsOnlyNulls();
   String person=jdbc.queryForObject("SELECT BIN_TO_UUID(person_id) FROM md_person_account WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",String.class,tenant,member);
   assertThat(person).isNotEqualToIgnoringCase(member).isNotEqualToIgnoringCase(account);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_person_organization WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND organization_id=UUID_TO_BIN(?) AND is_primary=TRUE",Integer.class,tenant,person,org)).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_person_position WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?)",Integer.class,tenant,person)).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_person_leader WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant)).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(account_id) FROM tenant_member WHERE id=UUID_TO_BIN(?)",String.class,member)).isEqualToIgnoringCase(account);
   assertThat(jdbc.queryForObject("SELECT password_hash FROM identity_account WHERE id=UUID_TO_BIN(?)",String.class,account)).isEqualTo("unused");
   assertThat(Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate().migrationsExecuted).isZero();
  }
 }
}
