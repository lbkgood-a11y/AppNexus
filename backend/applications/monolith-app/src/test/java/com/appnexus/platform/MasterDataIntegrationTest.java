package com.appnexus.platform;
import com.appnexus.masterdata.organization.PersonManagement;
import com.appnexus.masterdata.organization.PersonData;
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

/** 真实组合上下文验证全部 Flyway 迁移、五类对象唯一登记及权限/菜单契约。 */
@SpringBootTest
@ActiveProfiles("local")
class MasterDataIntegrationTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;
  @Autowired OperationRegistry registry;
  @Autowired org.springframework.core.env.Environment environment;
  @Autowired com.appnexus.platform.authorization.DataAccessCatalog dataAccess;
  @Autowired com.appnexus.masterdata.organization.PersonManagement service;
  @Autowired MasterDataManagement masterData;
  @Autowired com.appnexus.masterdata.api.masterdata.MasterDataReferenceNames referenceNames;
  @Autowired com.appnexus.bulkwarehousing.api.WarehouseBalances balances;
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
    var input=new com.appnexus.masterdata.organization.PersonData.Input("STAFF-1","独立测试人员",Map.of(),0);
    service.create(admin,com.appnexus.masterdata.organization.PersonData.Kind.PERSON,input);
    assertThat(service.list(admin,com.appnexus.masterdata.organization.PersonData.Kind.PERSON,"",null,1,20).total()).isEqualTo(1);
    assertThatThrownBy(()->service.list(ordinary,com.appnexus.masterdata.organization.PersonData.Kind.PERSON,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.create(ordinary,com.appnexus.masterdata.organization.PersonData.Kind.PERSON,input)).hasMessage("MASTER_DATA_DENIED");
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
    assertThatThrownBy(()->service.list(admin,com.appnexus.masterdata.organization.PersonData.Kind.PERSON,"",null,1,20)).hasMessage("MASTER_DATA_DENIED");
  }
  /** 批量目录保留同一权限准入，并对一批多个真实引用限定租户。 */
  @Test void referenceNamesBatchIsTenantAndPermissionScoped(){
    var tenant=UUID.randomUUID();
    var foreign=UUID.randomUUID();
    var account=UUID.randomUUID();
    var member=UUID.randomUUID();
    var ordinary=UUID.randomUUID();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'名称目录'),(UUID_TO_BIN(?),'其他租户')",tenant.toString(),foreign.toString());
    jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'目录管理员','unused')",account.toString(),"reference-"+account);
    jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
    bootstrap.initialize(tenant,member);
    var ids=List.of(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
    for(int i=0;i<ids.size();i++){
      jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",
          ids.get(i),(i==2?foreign:tenant).toString(),"REF-"+i,"仓库 "+i);
    }
    var product=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO md_product_v1(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-P','商品')",product,tenant.toString());
    var wanted=new ArrayList<com.appnexus.contracts.operation.ReferenceNames.Key>();
    ids.forEach(id->wanted.add(new com.appnexus.contracts.operation.ReferenceNames.Key("warehouse",id)));
    var productKey=new com.appnexus.contracts.operation.ReferenceNames.Key("product",product);
    wanted.add(productKey);
    var authorized=new com.appnexus.platform.tenant.VerifiedTenantContext(account,tenant,member,1);
    assertThat(referenceNames.load(authorized,wanted)).containsExactlyInAnyOrderEntriesOf(
        Map.of(wanted.get(0),"仓库 0",wanted.get(1),"仓库 1",productKey,"商品"));
    assertThat(referenceNames.load(new com.appnexus.platform.tenant.VerifiedTenantContext(account,tenant,ordinary,1),wanted)).isEmpty();
    var owner=UUID.randomUUID().toString();
    var unit=UUID.randomUUID().toString();
    String t=tenant.toString(), warehouse=ids.getFirst(), notice=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO md_partner(id,tenant_id,code,name,partner_type) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-O','货主','CUSTOMER')",owner,t);
    jdbc.update("INSERT INTO md_product_unit_group(tenant_id,code,name) VALUES(UUID_TO_BIN(?),'REF-COUNT','数量')",t);
    jdbc.update("INSERT INTO md_product_unit(id,tenant_id,code,name,dimension_code) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-UNIT','件','REF-COUNT')",unit,t);
    jdbc.update("""
        INSERT INTO bw_inbound_notice(tenant_id,id,notice_no,warehouse_id,owner_id,flow_version)
        VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'REF-N',UUID_TO_BIN(?),UUID_TO_BIN(?),1)
        """,t,notice,warehouse,owner);
    jdbc.update("""
        INSERT INTO bw_inbound_notice_line(tenant_id,id,notice_id,product_id,identity_kind,identity_id,
            product_version,base_unit_id,approved_quantity,received_quantity)
        VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'PRODUCT',UUID_TO_BIN(?),0,UUID_TO_BIN(?),100,80)
        """,t,notice,notice,product,product,unit);
    for(int i=0;i<2;i++){
      jdbc.update("""
          INSERT INTO bw_stock_receipt(tenant_id,receipt_id,inbound_quantity,pending_quantity,
            origin_warehouse_id,origin_owner_id,product_id,identity_kind,identity_id,unit_id)
          VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),40,40,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),
            'PRODUCT',UUID_TO_BIN(?),UUID_TO_BIN(?))
          """,t,UUID.randomUUID().toString(),warehouse,owner,product,product,unit);
    }
    var request=new org.springframework.mock.web.MockHttpServletRequest();
    request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(),authorized);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
        new org.springframework.web.context.request.ServletRequestAttributes(request));
    try{
      var filter=new com.appnexus.bulkwarehousing.api.WarehouseBalances.OverviewFilter(null,null,null);
      var first=balances.overview(authorized,filter,null,1);
      assertThat(first.nextAfterId()).isNotNull();
      var second=balances.overview(authorized,filter,first.nextAfterId(),1);
      assertThat(second.nextAfterId()).isNull();
      assertThat(List.of(first.rows().getFirst(),second.rows().getFirst())).allSatisfy(row->{
        assertThat(row.warehouse()).isEqualTo("仓库 0");
        assertThat(row.owner()).isEqualTo("货主");
        assertThat(row.product()).isEqualTo("商品");
        assertThat(row.unit()).isEqualTo("件");
        assertThat(row.warehouseId()).isEqualTo(warehouse);
        assertThat(row.unitId()).isEqualTo(unit);
      });
    }finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}

  }

  /** 大批量名称仍按类型与批次查询，权限检查次数与不同记录数无关。 */
  @Test void thousandMasterDataReferencesKeepQueriesBounded(){
    var tenant=UUID.randomUUID();
    var account=UUID.randomUUID();
    var member=UUID.randomUUID();
    String t=tenant.toString();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'主数据性能样本')",t);
    jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'名称压力账号','unused')",account.toString(),"reference-volume-"+account);
    jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),t,account.toString());
    bootstrap.initialize(tenant,member);
    var warehouseRows=new ArrayList<Object[]>();
    var productRows=new ArrayList<Object[]>();
    var keys=new ArrayList<com.appnexus.contracts.operation.ReferenceNames.Key>();
    for(int i=0;i<500;i++){
      String warehouse=UUID.randomUUID().toString(),product=UUID.randomUUID().toString();
      warehouseRows.add(new Object[]{warehouse,t,"VOLUME-W-"+i,"仓库 "+i});
      productRows.add(new Object[]{product,t,"VOLUME-P-"+i,"商品 "+i});
      keys.add(new com.appnexus.contracts.operation.ReferenceNames.Key("warehouse",warehouse));
      keys.add(new com.appnexus.contracts.operation.ReferenceNames.Key("product",product));
    }
    jdbc.batchUpdate("INSERT INTO md_warehouse(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",warehouseRows);
    jdbc.batchUpdate("INSERT INTO md_product_v1(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",productRows);
    var context=new com.appnexus.platform.tenant.VerifiedTenantContext(account,tenant,member,1);
    new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status->{
      long before=selectCount(),start=System.nanoTime();
      var result=com.appnexus.contracts.operation.ReferenceNames.resolveKeys(referenceNames,context,keys);
      long elapsed=(System.nanoTime()-start)/1_000_000,selects=selectCount()-before;
      assertThat(result).hasSize(1000).doesNotContainValue(null);
      assertThat(selects).isBetween(1L,100L);
      System.out.println("Master data references: 1000 references, "+selects+" SELECT including permissions, "+elapsed+" ms");
    });
  }

  /** 读取当前事务的 MySQL 会话计数，包含权限查询和名称投影 SQL。 */
  private long selectCount(){
    return Objects.requireNonNull(jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",(row,index)->row.getLong("Value")));
  }

  @Test void masterDataIsInstalledAndDeclaredExactlyOnce(){
    assertThat(registry.objects().stream().filter(o->Set.of("CountryRegion","Subdivision","StorageArea","StorageLocation").contains(o.code())).toList())
        .hasSize(4).allSatisfy(o->{assertThat(o.configurable()).isTrue();assertThat(o.operations()).hasSize(4);});
    var objects=registry.objects().stream().filter(o->Set.of("Person","BusinessPartner","Warehouse","CountryRegion","Product").contains(o.code())).toList();
    assertThat(objects).hasSize(5);
    assertThat(objects.stream().filter(o->o.code().equals("Person")).findFirst().orElseThrow().module()).isEqualTo("organization");
    assertThat(objects.stream().filter(o->!o.code().equals("Person"))).allSatisfy(o->assertThat(o.module()).isEqualTo("master-data"));
    // 明确固定操作与真实引擎启用时的审批读入口，避免通过观察目录推导期望值。
    var productOperations=new ArrayList<>(List.of(
        "list","create","setStatus","read","update","archiveDraft","restoreDraft","readChanges",
        "previewStructure","migrateStructure","options","readConfiguration","listConfigurations",
        "reviewLegacySpecification","stageLegacy","reconcileLegacy","readMeasurement","saveMeasurement",
        "calculatePackaging","readStructure","createSpecification","createSku","addComponent","setStructureState",
        "listImages","uploadImage","readImage","detachImage","createOrganizations","updateOrganizations"));
    if(environment.getProperty("app.workflow.engine.enabled",Boolean.class,false)){
      productOperations.addAll(List.of("readApprovals","readApprovalOrganizations","readApprovalSnapshot"));
    }
    assertThat(objects).allSatisfy(o->{
      assertThat(o.category()).isEqualTo("MASTER_DATA");
      if(o.code().equals("Product")) {
        assertThat(o.operations()).extracting(OperationRegistry.Definition::operationCode)
            .containsExactlyInAnyOrderElementsOf(productOperations);
      } else {
        assertThat(o.operations()).hasSize(o.code().equals("Person")?16:o.code().equals("Warehouse")?7:4);
      }
    });
    var operations=registry.all().stream().filter(o->Set.of("Person","BusinessPartner","Warehouse","CountryRegion","Product").contains(o.objectType())).toList();
    assertThat(operations).hasSize(31+productOperations.size());
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
            "OrganizationType","OrganizationRelation","Position","TenantParameter","BusinessDictionary","TenantRole")
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
