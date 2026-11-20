package com.appnexus.platform;
import com.appnexus.platform.security.operation.OperationRegistry;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.appnexus.masterdata.application.masterdata.MasterDataService;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 组合上下文真实RBAC、对象登记和迁移验证；隔离测试租户，不修改管理员凭据。 */
@SpringBootTest
@ActiveProfiles("local")
class StorageHierarchyIntegrationTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired MasterDataService service;
  @Autowired OperationRegistry registry;
  @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

  /** 新租户管理员具备独立权限，普通成员及撤权成员不能读写库区和库位。 */
  @Test void storagePermissionsAreRealAndRevocable(){
    UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID(),ordinaryAccount=UUID.randomUUID(),ordinaryMember=UUID.randomUUID();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'库区库位独立测试租户')",tenant.toString());
    DictionaryFixtures.seed(jdbc,tenant,null);
    for(var pair:List.of(Map.entry(account,member),Map.entry(ordinaryAccount,ordinaryMember))){
      jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'库区库位测试成员','unused')",pair.getKey().toString(),"storage-"+pair.getKey());
      jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",pair.getValue().toString(),tenant.toString(),pair.getKey().toString());
    }
    bootstrap.initialize(tenant,member);
    var admin=new VerifiedTenantContext(account,tenant,member,1);
    var ordinary=new VerifiedTenantContext(ordinaryAccount,tenant,ordinaryMember,1);
    service.create(admin,Kind.WAREHOUSE,new Input("W","仓库",Map.of(),0));
    service.create(admin,Kind.STORAGE_AREA,new Input("A","库区",Map.of("parentCode","W"),0));
    service.create(admin,Kind.STORAGE_LOCATION,new Input("L","库位",Map.of("parentCode","A"),0));
    for(var kind:List.of(Kind.STORAGE_AREA,Kind.STORAGE_LOCATION)){
      assertThat(service.list(admin,kind,"",null,1,20).total()).isEqualTo(1);
      assertThatThrownBy(()->service.list(ordinary,kind,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
      assertThatThrownBy(()->service.create(ordinary,kind,new Input("DENIED","无权写入",Map.of("parentCode",kind==Kind.STORAGE_AREA?"W":"A"),0))).hasMessage("MASTER_DATA_DENIED");
    }
    jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
    for(var kind:List.of(Kind.STORAGE_AREA,Kind.STORAGE_LOCATION))assertThatThrownBy(()->service.list(admin,kind,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
  }

  /** 两个真实主数据对象各声明四个操作，页面和权限均有唯一持久化来源。 */
  @Test void storageObjectsAndPageAreDeclared(){
    var objects=registry.objects().stream().filter(o->Set.of("StorageArea","StorageLocation").contains(o.code())).toList();
    assertThat(objects).hasSize(2).allSatisfy(o->{assertThat(o.module()).isEqualTo("master-data");assertThat(o.operations()).hasSize(4);});
    var operations=registry.all().stream().filter(o->Set.of("StorageArea","StorageLocation").contains(o.objectType())).toList();
    assertThat(operations).hasSize(8).allSatisfy(o->{
      assertThat(o.pages()).contains("master-data/storage");
      for(String permission:o.permissions())assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=?",Integer.class,permission)).isEqualTo(1);
    });
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_menu WHERE component_key='master-data/storage' AND route_path='/master-data/storage'",Integer.class)).isEqualTo(1);
  }
}
