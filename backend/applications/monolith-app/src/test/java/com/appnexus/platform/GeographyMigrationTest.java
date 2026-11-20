package com.appnexus.platform;
import com.appnexus.masterdata.bootstrap.MasterDataConfiguration;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import com.appnexus.masterdata.adapter.persistence.masterdata.GeographyDefaults;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 证明旧租户升级、幂等、新租户事件及审计边界，迁移版本不得与宿主重号。 */
class GeographyMigrationTest {
  @Test void upgradesExistingTenantsWithoutOverwritingCustomizedRecords(){
    try(var mysql=new MySQLContainer("mysql:8.4")){
      mysql.start();var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var jdbc=new JdbcTemplate(source);
      Flyway.configure().dataSource(source).target("94").load().migrate();
      UUID tenant=UUID.randomUUID(),other=UUID.randomUUID(),actor=UUID.randomUUID();
      jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'地理迁移租户')",tenant.toString());
      jdbc.update("INSERT INTO md_subdivision(id,tenant_id,code,name,country_code,subdivision_type,parent_code,enabled,version) VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(?),'CN-FJ','福建自定义','CN','Province','',FALSE,3)",tenant.toString());
      String customized="SELECT BIN_TO_UUID(id) id,name,enabled,version,created_at,updated_at,BIN_TO_UUID(created_by) created_by,BIN_TO_UUID(updated_by) updated_by FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?) AND code='CN-FJ'";
      var before=jdbc.queryForMap(customized,tenant.toString());
      Flyway.configure().dataSource(source).load().migrate();
      assertThat(jdbc.queryForMap(customized,tenant.toString())).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?) AND code LIKE 'CN-MCA-%'",Integer.class,tenant.toString())).isEqualTo(3178);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_country_region WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,tenant.toString())).isEqualTo(249);
      var row=jdbc.queryForMap("SELECT parent_code,created_by,updated_by,created_at,updated_at FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?) AND code='CN-MCA-350102'",tenant.toString());
      assertThat(row.get("parent_code")).isEqualTo("CN-MCA-350100");assertThat(row.get("created_by")).isNull();assertThat(row.get("updated_by")).isNull();assertThat(row.get("created_at")).isNotNull();
      assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,other.toString())).isZero();
      var catalog=new com.appnexus.masterdata.JdbcGeographyCatalog(jdbc);
      assertThat(catalog.nodes(tenant)).hasSize(8473);
      assertThat(catalog.nodes(other)).isEmpty();
      assertThat(catalog.nodes(tenant).stream().filter(n->n.code().equals("CN-FJ")).findFirst().orElseThrow().enabled()).isFalse();
      jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'新租户')",other.toString());
      new com.appnexus.masterdata.bootstrap.MasterDataConfiguration.GeographyProvisioning(jdbc).provision(new com.appnexus.platform.tenant.TenantProvisioned(other,actor));
      GeographyDefaults.seed(jdbc,other,actor);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?) AND created_by=UUID_TO_BIN(?) AND updated_by=UUID_TO_BIN(?)",Integer.class,other.toString(),actor.toString(),actor.toString())).isEqualTo(8224);
    }
  }
}
