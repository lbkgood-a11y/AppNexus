package com.appnexus.platform;
import com.appnexus.platform.security.operation.OperationRegistry;
import com.appnexus.platform.authorization.*;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.ActiveProfiles;
import com.appnexus.masterdata.application.masterdata.MasterDataService;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 组合上下文真实RBAC、对象登记和迁移验证；隔离测试租户，不修改管理员凭据。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StorageHierarchyIntegrationTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired MasterDataService service;
  @Autowired OperationRegistry registry;
  @Autowired RolePermissionManagement permissions;
  @Autowired DataPolicyMetadataCatalog metadata;
  @Autowired com.appnexus.masterdata.adapter.persistence.MasterRecordMapper records;
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

  /** 原授权用例保存指定仓库，仓库及两级子记录均在真实 MySQL 查询或写入前裁剪。 */
  @Test void selectedWarehouseScopeControlsStorageReadsAndWrites() throws Exception {
    UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'仓库记录范围测试')",tenant.toString());
    DictionaryFixtures.seed(jdbc,tenant,null);
    jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'仓储范围成员','unused')",account.toString(),"warehouse-"+account);
    jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
    bootstrap.initialize(tenant,member);
    var context=new VerifiedTenantContext(account,tenant,member,1);
    var admin=new RoleManagement.Context(account,tenant,member);
    UUID role=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(?) AND system_key LIKE 'TENANT_ADMIN:%'",String.class,tenant.toString()));
    Map<String,String> visible=storageTree(context,"A"),hidden=storageTree(context,"B");
    var before=permissions.getAuthorization(admin,role);
    var supported=metadata.list(PermissionCatalog.ScopeType.TENANT).stream().filter(s->s.resourceType().equals("masterdata.warehouse")).toList();
    assertThat(supported).hasSize(16);
    var scoped=supported.stream().filter(s->s.scopeType()==DataScopeAuthorization.ScopeType.RULES&&!s.permissionCode().endsWith(":create"))
        .map(DataPolicyMetadataCatalog.ScopeSupport::permissionId).collect(java.util.stream.Collectors.toSet());
    assertThat(scoped).hasSize(3);
    var warehousePermissions=new HashSet<>(supported.stream().map(DataPolicyMetadataCatalog.ScopeSupport::permissionId).toList());
    for(var kind:List.of(Kind.STORAGE_AREA,Kind.STORAGE_LOCATION)){
      warehousePermissions.addAll(jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM access_permission WHERE scope_type='TENANT' AND resource=?",String.class,kind.resource()).stream().map(UUID::fromString).toList());
    }
    warehousePermissions.add(UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code='organization.permission:grant'",String.class)));
    var policies=new HashSet<>(before.dataPolicies().stream().filter(p->warehousePermissions.contains(p.permissionId())).toList());
    policies.removeIf(policy->scoped.contains(policy.permissionId()));
    for(UUID id:scoped)policies.add(new RolePermissionManagement.DataPolicy(id,DataScopeAuthorization.ScopeType.RULES,
        "masterdata.warehouse",Set.of(),List.of(new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),
        Set.of(UUID.fromString(visible.get("warehouse"))),List.of(),false))));
    permissions.replaceAuthorization(admin,role,before.roleVersion(),warehousePermissions,policies,"仓储范围定向回归");
    jdbc.update("UPDATE access_member_authorization SET inherit_data_scope=TRUE WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?) AND role_id=UUID_TO_BIN(?)",tenant.toString(),member.toString(),role.toString());
    for(var kind:List.of(Kind.WAREHOUSE,Kind.STORAGE_AREA,Kind.STORAGE_LOCATION)){
      String key=kind==Kind.WAREHOUSE?"warehouse":kind==Kind.STORAGE_AREA?"area":"location";
      var page=service.list(context,kind,"",null,1,20);
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.rows()).extracting(Row::id).containsExactly(visible.get(key));
      assertThat(service.list(context,kind,"",null,1,20,hidden.get(key)).total()).isZero();
      var attrs=kind==Kind.WAREHOUSE?Map.<String,String>of():Map.of("parentCode",kind==Kind.STORAGE_AREA?"WB":"AB");
      assertThatThrownBy(()->service.update(context,kind,hidden.get(key),new Input("B","越权修改",attrs,0))).hasMessage("MASTER_DATA_DENIED");
      assertThatThrownBy(()->service.setStatus(context,kind,hidden.get(key),new Status(false,0))).hasMessage("MASTER_DATA_DENIED");
      assertThatThrownBy(()->records.status(tenant.toString(),kind,visible.get(key),false,0))
          .hasRootCauseMessage("SCOPED_WRITE_PLAN_REQUIRED");
    }
    assertThatThrownBy(()->service.create(context,Kind.STORAGE_AREA,new Input("NEW","越权库区",Map.of("parentCode","WB"),0))).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.create(context,Kind.STORAGE_LOCATION,new Input("NEW","越权库位",Map.of("parentCode","AB"),0))).hasMessage("MASTER_DATA_DENIED");
    UUID read=supported.stream().filter(item->item.permissionCode().equals("masterdata.warehouse:read")).findFirst().orElseThrow().permissionId();
    var condition=new DataGrantRules.Condition("warehouseType",DataGrantRules.Operator.EQ,List.of("PHYSICAL"));
    var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(condition),false);
    var conditionPolicies=new HashSet<>(policies);
    conditionPolicies.removeIf(policy->policy.permissionId().equals(read));
    conditionPolicies.add(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.RULES,"masterdata.warehouse",Set.of(),List.of(rule)));
    var current=permissions.getAuthorization(admin,role);
    var after=permissions.replaceAuthorization(admin,role,current.roleVersion(),warehousePermissions,conditionPolicies,"验证仓库类型条件");
    for(var kind:List.of(Kind.WAREHOUSE,Kind.STORAGE_AREA,Kind.STORAGE_LOCATION)){
      String key=kind==Kind.WAREHOUSE?"warehouse":kind==Kind.STORAGE_AREA?"area":"location";
      assertThat(service.list(context,kind,"",null,1,20).rows()).extracting(Row::id).containsExactly(visible.get(key));
      assertThat(service.list(context,kind,"",null,1,20,hidden.get(key)).total()).isZero();
    }
    // 另换经营模式条件，使用相反的可见仓库，证明过滤依据来自新维度。
    var modeCondition=new DataGrantRules.Condition("operatingMode",DataGrantRules.Operator.IN,List.of("OUTSOURCED"));
    var modeRule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(modeCondition),false);
    var modePolicies=new HashSet<>(conditionPolicies);
    modePolicies.removeIf(policy->policy.permissionId().equals(read));
    modePolicies.add(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.RULES,"masterdata.warehouse",Set.of(),List.of(modeRule)));
    after=permissions.replaceAuthorization(admin,role,after.roleVersion(),warehousePermissions,modePolicies,"验证经营模式条件");
    for(var kind:List.of(Kind.WAREHOUSE,Kind.STORAGE_AREA,Kind.STORAGE_LOCATION)){
      String key=kind==Kind.WAREHOUSE?"warehouse":kind==Kind.STORAGE_AREA?"area":"location";
      var page=service.list(context,kind,"",null,1,20);
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.rows()).extracting(Row::id).containsExactly(hidden.get(key));
      assertThat(service.list(context,kind,"",null,1,20,visible.get(key)).total()).isZero();
    }
    after=permissions.replaceAuthorization(admin,role,after.roleVersion(),warehousePermissions,
        before.dataPolicies().stream().filter(p->warehousePermissions.contains(p.permissionId())).collect(java.util.stream.Collectors.toSet()),
        "恢复完整范围以创建交集测试记录");
    var physicalOutsourced=storageTree(context,"C","PHYSICAL","OUTSOURCED");
    var both=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(condition,modeCondition),false);
    var combinedPolicies=new HashSet<>(conditionPolicies);
    combinedPolicies.removeIf(policy->policy.permissionId().equals(read));
    combinedPolicies.add(new RolePermissionManagement.DataPolicy(read,DataScopeAuthorization.ScopeType.RULES,"masterdata.warehouse",Set.of(),List.of(both)));
    after=permissions.replaceAuthorization(admin,role,after.roleVersion(),warehousePermissions,combinedPolicies,"验证两个仓库条件取交集");
    var selected=mvc.perform(post("/bff/tenants/current").with(authenticated(account.toString(),0)).with(csrf())
        .contentType("application/json").content("""
            {"tenantId":"%s"}
            """.formatted(tenant)))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("JSESSIONID");
    assertThat(selected).isNotNull();
    for(var endpoint:Map.of("warehouse","warehouse","area","storage-area","location","storage-location").entrySet()){
      var path="/api/master-data/"+endpoint.getValue();
      var key=endpoint.getKey();
      mvc.perform(get(path).cookie(selected).param("size","1"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.rows[0].id").value(physicalOutsourced.get(key)));
      for(var excluded:List.of(visible,hidden)){
        mvc.perform(get(path).cookie(selected).param("id",excluded.get(key)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.rows").isEmpty());
      }
    }
    after=permissions.replaceAuthorization(admin,role,after.roleVersion(),warehousePermissions,conditionPolicies,"恢复仓库类型条件");
    UUID update=supported.stream().filter(item->item.permissionCode().equals("masterdata.warehouse:update")).findFirst().orElseThrow().permissionId();
    var invalid=new HashSet<>(conditionPolicies);
    invalid.removeIf(policy->policy.permissionId().equals(update));
    invalid.add(new RolePermissionManagement.DataPolicy(update,DataScopeAuthorization.ScopeType.RULES,"masterdata.warehouse",Set.of(),List.of(modeRule)));
    long readOnlyVersion=after.roleVersion();
    assertThatThrownBy(()->permissions.replaceAuthorization(admin,role,readOnlyVersion,warehousePermissions,invalid,"禁止按条件修改"))
        .hasMessage("DATA_GRANT_FIELD_UNSUPPORTED");
    service.update(context,Kind.WAREHOUSE,visible.get("warehouse"),new Input("WA","可见仓库",Map.of("warehouseType","PHYSICAL"),0));
    service.setStatus(context,Kind.STORAGE_LOCATION,visible.get("location"),new Status(false,0));
    service.setStatus(context,Kind.STORAGE_AREA,visible.get("area"),new Status(false,0));
    assertThatThrownBy(()->service.update(context,Kind.WAREHOUSE,visible.get("warehouse"),
        new Input("WA","旧版本编辑",Map.of("warehouseType","PHYSICAL"),0))).hasMessage("MASTER_DATA_CHANGED");
    assertThatThrownBy(()->service.setStatus(context,Kind.WAREHOUSE,visible.get("warehouse"),new Status(false,0)))
        .hasMessage("MASTER_DATA_CHANGED");
    service.setStatus(context,Kind.WAREHOUSE,visible.get("warehouse"),new Status(false,1));
    assertThat(service.list(context,Kind.WAREHOUSE,"",false,1,20,visible.get("warehouse")).rows()).extracting(Row::id).containsExactly(visible.get("warehouse"));
  }

  /** 同租户两棵仓储树区分授权记录与未授权记录。 */
  private Map<String,String> storageTree(VerifiedTenantContext context,String suffix){
    return storageTree(context,suffix,suffix.equals("A")?"PHYSICAL":"VIRTUAL",suffix.equals("A")?"SELF_OPERATED":"OUTSOURCED");
  }

  /** 建立给定仓库类型和经营模式的完整三层测试数据。 */
  private Map<String,String> storageTree(VerifiedTenantContext context,String suffix,String type,String mode){
    String warehouse=service.create(context,Kind.WAREHOUSE,new Input("W"+suffix,"仓库"+suffix,Map.of("warehouseType",type,"operatingMode",mode),0)).get("id");
    String area=service.create(context,Kind.STORAGE_AREA,new Input("A"+suffix,"库区"+suffix,Map.of("parentCode","W"+suffix),0)).get("id");
    String location=service.create(context,Kind.STORAGE_LOCATION,new Input("L"+suffix,"库位"+suffix,Map.of("parentCode","A"+suffix),0)).get("id");
    return Map.of("warehouse",warehouse,"area",area,"location",location);
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
