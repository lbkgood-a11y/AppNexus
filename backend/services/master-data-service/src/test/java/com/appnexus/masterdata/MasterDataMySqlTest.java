package com.appnexus.masterdata;

import static org.assertj.core.api.Assertions.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.mysql.MySQLContainer;
import static com.appnexus.masterdata.MasterData.*;

/** 真实 MySQL 验证迁移建表、编码唯一、跨租户同 ID、并发版本及权限撤销。 */
@Tag("container")
class MasterDataMySqlTest {
  @Test void allKindsEnforceTenantAndConcurrentVersion() throws Exception {
    try(var mysql=new MySQLContainer("mysql:8.4")){
      mysql.start();
      var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
      var jdbc=new JdbcTemplate(source);
      // 最小宿主权限/导航表，仅供完整执行本模块迁移；不读取或修改开发数据库。
      String host="""
        CREATE TABLE access_permission(id BINARY(16),scope_type VARCHAR(20),code VARCHAR(150),resource VARCHAR(100),action VARCHAR(50),name_zh VARCHAR(200),name_en VARCHAR(200),description VARCHAR(500));
        CREATE TABLE platform_administrator_bootstrap(slot INT,account_id BINARY(16));
        CREATE TABLE access_role(id BINARY(16),system_key VARCHAR(100),version BIGINT DEFAULT 0);
        CREATE TABLE access_role_permission(role_id BINARY(16),permission_id BINARY(16),scope_type VARCHAR(20),granted_by BINARY(16));
        CREATE TABLE access_member_authorization(tenant_id BINARY(16),member_id BINARY(16),role_id BINARY(16));
        CREATE TABLE access_member_authorization_permission(tenant_id BINARY(16),member_id BINARY(16),role_id BINARY(16),permission_id BINARY(16));
        CREATE TABLE platform_menu(id BINARY(16),parent_id BINARY(16),type VARCHAR(20),code VARCHAR(150),title_zh VARCHAR(200),title_en VARCHAR(200),route_path VARCHAR(200),component_key VARCHAR(200),icon VARCHAR(100),permission_code VARCHAR(150),sort_order INT,scope_type VARCHAR(20));
        CREATE TABLE platform_menu_resource(menu_id BINARY(16),resource_code VARCHAR(150));
        INSERT INTO access_role(id,system_key) VALUES(UUID_TO_BIN(UUID()),'TENANT_ADMIN:test'),(UUID_TO_BIN(UUID()),'CUSTOM_ROLE');
        """;
      try(var connection=source.getConnection()){
        ScriptUtils.executeSqlScript(connection,new ByteArrayResource(host.getBytes(StandardCharsets.UTF_8)));
        ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V75__master_data.sql"));
        ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V76__personnel_profiles.sql"));
        // 测试宿主仅部署 V75/V76；V83 的账号和组织关系表属于组合迁移测试，不在此伪造。
        String auditSql=new String(new ClassPathResource("db/migration/V83__global_record_audit.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        for(String statement:auditSql.split(";")){
          var table=java.util.regex.Pattern.compile("ALTER TABLE `([^`]+)`").matcher(statement);
          if(table.find()&&Set.of("md_partner","md_warehouse","md_country_region","md_product","md_person_detail","md_person_user_assignment").contains(table.group(1)))
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource((statement+";").getBytes(StandardCharsets.UTF_8)));
        }
      }
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission",Integer.class)).isEqualTo(24);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_role_permission",Integer.class)).isEqualTo(24);
      var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
      var other=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
      var revoked=new java.util.concurrent.atomic.AtomicBoolean(false);
      AccessAuthorization access=(subject,permission)->!revoked.get()&&jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=?",Integer.class,permission)==1;
      var service=new MasterDataService(jdbc,access,new DataSourceTransactionManager(source));
      for(var kind:Kind.values()){
        Map<String,String> attrs=switch(kind){
          case PARTNER->Map.of("partnerType","BOTH");
          case PRODUCT->Map.of("unit","件");
          case COUNTRY_REGION->Map.of("alpha3Code","CHN","numericCode","156");
          default->Map.of();
        };
        var input=new Input(kind==Kind.COUNTRY_REGION?"CN":"CODE-1","原始资料",attrs,0);
        String id=service.create(c,kind,input).get("id");
        assertThat(service.list(c,kind,"",null,1,20).total()).isEqualTo(1);
        assertThat(service.list(other,kind,"",null,1,20).total()).isZero();
        // 同编码允许存在于不同租户，但同 ID 不能读取或覆盖。
        service.create(other,kind,input);
        assertThatThrownBy(()->service.update(other,kind,id,input)).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.setStatus(other,kind,id,new Status(false,0))).isInstanceOf(Rejection.class);
        assertThatThrownBy(()->service.create(c,kind,input)).isInstanceOf(Rejection.class).hasMessage("MASTER_DATA_CODE_CONFLICT");
        var edit=new Input(input.code(),"更新资料",attrs,0);
        try(var executor=Executors.newFixedThreadPool(2)){
          Callable<Boolean> update=()->{try{service.update(c,kind,id,edit);return true;}catch(Rejection conflict){assertThat(conflict.status).isEqualTo(409);return false;}};
          var results=executor.invokeAll(List.of(update,update));
          assertThat(results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
        }
        var row=service.list(c,kind,"更新",null,1,20).rows().getFirst();
        assertThat(row.version()).isEqualTo(1);
        service.setStatus(c,kind,id,new Status(false,1));
        assertThat(service.list(c,kind,"",true,1,20).total()).isZero();
        assertThat(service.list(c,kind,"",false,1,20).rows().getFirst().version()).isEqualTo(2);
        assertThatThrownBy(()->service.setStatus(c,kind,id,new Status(true,1))).isInstanceOf(Rejection.class);
      }
      revoked.set(true);
      assertThatThrownBy(()->service.list(c,Kind.PARTNER,"",null,1,20)).isInstanceOf(Rejection.class).hasMessage("MASTER_DATA_DENIED");
    }
  }
}
