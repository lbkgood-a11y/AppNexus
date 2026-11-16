package com.appnexus.platform;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.appnexus.platform.configuration.NavigationPages;

/** 真实组合上下文验证全部 Flyway 迁移、五类对象与 20 个操作唯一登记及权限/菜单契约。 */
@SpringBootTest
@ActiveProfiles("local")
class MasterDataIntegrationTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired OperationRegistry registry;
  @Autowired com.appnexus.platform.organization.personnel.PersonDataService service;
  @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
  /** 新租户管理员及普通成员使用真实 RBAC 执行器，验证新权限不会向普通成员扩散。 */
  @Test void administratorCanManageButOrdinaryAndRevokedMembersCannot(){
    UUID tenant=UUID.randomUUID(),adminAccount=UUID.randomUUID(),adminMember=UUID.randomUUID(),ordinaryAccount=UUID.randomUUID(),ordinaryMember=UUID.randomUUID();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'主数据集成测试租户')",tenant.toString());
    for(var pair:List.of(Map.entry(adminAccount,adminMember),Map.entry(ordinaryAccount,ordinaryMember))){
      jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'独立测试成员','unused')",pair.getKey().toString(),"md-"+pair.getKey());
      jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",pair.getValue().toString(),tenant.toString(),pair.getKey().toString());
    }
    bootstrap.initialize(tenant,adminMember);
    var admin=new com.appnexus.platform.tenant.VerifiedTenantContext(adminAccount,tenant,adminMember,1);
    var ordinary=new com.appnexus.platform.tenant.VerifiedTenantContext(ordinaryAccount,tenant,ordinaryMember,1);
    var input=new com.appnexus.platform.organization.personnel.PersonData.Input("STAFF-1","独立测试人员",Map.of(),0);
    service.create(admin,com.appnexus.platform.organization.personnel.PersonData.Kind.PERSON,input);
    assertThat(service.list(admin,com.appnexus.platform.organization.personnel.PersonData.Kind.PERSON,"",null,1,20).total()).isEqualTo(1);
    assertThatThrownBy(()->service.list(ordinary,com.appnexus.platform.organization.personnel.PersonData.Kind.PERSON,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.create(ordinary,com.appnexus.platform.organization.personnel.PersonData.Kind.PERSON,input)).hasMessage("MASTER_DATA_DENIED");
    jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),adminMember.toString());
    assertThatThrownBy(()->service.list(admin,com.appnexus.platform.organization.personnel.PersonData.Kind.PERSON,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
  }
  @Test void masterDataIsInstalledAndDeclaredExactlyOnce(){
    var objects=registry.objects().stream().filter(o->Set.of("Person","BusinessPartner","Warehouse","CountryRegion","Product").contains(o.code())).toList();
    assertThat(objects).hasSize(5);
    assertThat(objects.stream().filter(o->o.code().equals("Person")).findFirst().orElseThrow().module()).isEqualTo("organization");
    assertThat(objects.stream().filter(o->!o.code().equals("Person"))).allSatisfy(o->assertThat(o.module()).isEqualTo("master-data"));
    assertThat(objects).allSatisfy(o->{assertThat(o.category()).isEqualTo("MASTER_DATA");assertThat(o.operations()).hasSize(o.code().equals("Person")?15:4);});
    var operations=registry.all().stream().filter(o->Set.of("Person","BusinessPartner","Warehouse","CountryRegion","Product").contains(o.objectType())).toList();
    assertThat(operations).hasSize(31);
    for(var operation:operations){
      for(String permission:operation.permissions())assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=? AND scope_type='TENANT'",Integer.class,permission)).isEqualTo(1);
      for(String key:operation.pages()){
        var page=NavigationPages.find(key);assertThat(page).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_menu WHERE component_key=? AND route_path=? AND permission_code=?",Integer.class,key,page.path(),page.permission())).isEqualTo(1);
      }
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='75' AND success=1",Integer.class)).isEqualTo(1);
  }
}
