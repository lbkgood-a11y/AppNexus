package com.appnexus.platform;
import com.appnexus.platform.organization.PersonManagement;
import com.appnexus.platform.organization.PersonData;
import com.appnexus.platform.security.operation.OperationRegistry;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.appnexus.platform.configuration.NavigationPages;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.masterdata.MasterData.Kind;

/** 真实组合上下文验证全部 Flyway 迁移、五类对象与 20 个操作唯一登记及权限/菜单契约。 */
@SpringBootTest
@ActiveProfiles("local")
class MasterDataIntegrationTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired OperationRegistry registry;
  @Autowired com.appnexus.platform.authorization.DataAccessCatalog dataAccess;
  @Autowired com.appnexus.platform.organization.PersonManagement service;
  @Autowired MasterDataManagement masterData;
  @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
  /** 新租户管理员及普通成员使用真实 RBAC 执行器，验证新权限不会向普通成员扩散。 */
  @Test void administratorCanManageButOrdinaryAndRevokedMembersCannot(){
    UUID tenant=UUID.randomUUID(),adminAccount=UUID.randomUUID(),adminMember=UUID.randomUUID(),ordinaryAccount=UUID.randomUUID(),ordinaryMember=UUID.randomUUID();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'主数据集成测试租户')",tenant.toString());
    DictionaryFixtures.seed(jdbc,tenant,null);
    for(var pair:List.of(Map.entry(adminAccount,adminMember),Map.entry(ordinaryAccount,ordinaryMember))){
      jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'独立测试成员','unused')",pair.getKey().toString(),"md-"+pair.getKey());
      jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",pair.getValue().toString(),tenant.toString(),pair.getKey().toString());
    }
    bootstrap.initialize(tenant,adminMember);
    var admin=new com.appnexus.platform.tenant.VerifiedTenantContext(adminAccount,tenant,adminMember,1);
    var ordinary=new com.appnexus.platform.tenant.VerifiedTenantContext(ordinaryAccount,tenant,ordinaryMember,1);
    var input=new com.appnexus.platform.organization.PersonData.Input("STAFF-1","独立测试人员",Map.of(),0);
    service.create(admin,com.appnexus.platform.organization.PersonData.Kind.PERSON,input);
    assertThat(service.list(admin,com.appnexus.platform.organization.PersonData.Kind.PERSON,"",null,1,20).total()).isEqualTo(1);
    assertThatThrownBy(()->service.list(ordinary,com.appnexus.platform.organization.PersonData.Kind.PERSON,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.create(ordinary,com.appnexus.platform.organization.PersonData.Kind.PERSON,input)).hasMessage("MASTER_DATA_DENIED");
    for(var kind:List.of(Kind.COUNTRY_REGION,Kind.SUBDIVISION)){
      assertThat(masterData.geographyTree(admin,kind,"",1,20)).isNotNull();
      assertThatThrownBy(()->masterData.geographyTree(ordinary,kind,"",1,20)).hasMessage("MASTER_DATA_DENIED");
      assertThatThrownBy(()->masterData.create(ordinary,kind,null)).hasMessage("MASTER_DATA_DENIED");
    }
    for(var kind:List.of(Kind.STORAGE_AREA,Kind.STORAGE_LOCATION)){
      assertThat(masterData.list(admin,kind,"",null,1,20).total()).isZero();
      assertThatThrownBy(()->masterData.list(ordinary,kind,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
      assertThatThrownBy(()->masterData.create(ordinary,kind,null)).hasMessage("MASTER_DATA_DENIED");
    }
    jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),adminMember.toString());
    assertThatThrownBy(()->service.list(admin,com.appnexus.platform.organization.PersonData.Kind.PERSON,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
  }
  @Test void masterDataIsInstalledAndDeclaredExactlyOnce(){
    assertThat(registry.objects().stream().filter(o->Set.of("CountryRegion","Subdivision","StorageArea","StorageLocation").contains(o.code())).toList())
        .hasSize(4).allSatisfy(o->{assertThat(o.configurable()).isTrue();assertThat(o.operations()).hasSize(4);});
    var objects=registry.objects().stream().filter(o->Set.of("Person","BusinessPartner","Warehouse","CountryRegion","Product").contains(o.code())).toList();
    assertThat(objects).hasSize(5);
    assertThat(objects.stream().filter(o->o.code().equals("Person")).findFirst().orElseThrow().module()).isEqualTo("organization");
    assertThat(objects.stream().filter(o->!o.code().equals("Person"))).allSatisfy(o->assertThat(o.module()).isEqualTo("master-data"));
    // 审批入口仅随真实引擎装配；默认关闭时基础27项，启用时再增加3个真实只读入口。
    int productOperations=registry.all().stream().anyMatch(o->o.id().equals("Product.readApprovals"))?30:27;
    assertThat(objects).allSatisfy(o->{assertThat(o.category()).isEqualTo("MASTER_DATA");assertThat(o.operations()).hasSize(o.code().equals("Person")?16:o.code().equals("Product")?productOperations:o.code().equals("Warehouse")?7:4);});
    var operations=registry.all().stream().filter(o->Set.of("Person","BusinessPartner","Warehouse","CountryRegion","Product").contains(o.objectType())).toList();
    assertThat(operations).hasSize(31+productOperations);
    for(var operation:operations){
      for(String expression:operation.permissions())for(String permission:expression.split("&"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=? AND scope_type='TENANT'",Integer.class,permission)).isEqualTo(1);
      for(String key:operation.pages()){
        var page=NavigationPages.find(key);assertThat(page).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_menu WHERE component_key=? AND route_path=? AND permission_code=?",Integer.class,key,page.path(),page.permission())).isEqualTo(1);
      }
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='75' AND success=1",Integer.class)).isEqualTo(1);
  }

  /** 固定域资源必须从唯一 Provider 投影，且不向角色管理开放未执行的可配置行范围。 */
  @Test void fixedScopeProviderResourcesAreRegisteredInTheirOwnDomains() {
    var scope = com.appnexus.platform.authorization.PermissionCatalog.ScopeType.TENANT;
    var tenant = dataAccess.list(scope).stream()
        .filter(descriptor -> Set.of("ProductCategory","ProductUnit","ProductAttribute","ProductTemplate",
            "OrganizationType","OrganizationRelation","Position","TenantParameter","BusinessDictionary","TenantRole","OrganizationAuthorization")
            .contains(descriptor.objectType())).toList();
    assertThat(tenant).hasSize(11).allSatisfy(descriptor -> assertThat(descriptor.operations()).isEmpty());
    assertThat(tenant.stream().flatMap(descriptor -> descriptor.resources().stream())
        .map(resource -> resource.resource()).toList()).contains("masterdata.productunit.conversion",
            "masterdata.producttemplate.item","configuration.dictionary.item","access.tenant.role.authorization");
    assertThat(dataAccess.list(com.appnexus.platform.authorization.PermissionCatalog.ScopeType.PLATFORM))
        .anySatisfy(descriptor -> {
          assertThat(descriptor.objectType()).isEqualTo("PlatformRole");
          assertThat(descriptor.resources().stream().map(resource -> resource.resource()).toList())
              .containsExactly("access.role","access.role.authorization");
        });
  }
}
