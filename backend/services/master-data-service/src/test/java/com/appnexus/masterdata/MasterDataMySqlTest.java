package com.appnexus.masterdata;
import com.appnexus.masterdata.adapter.persistence.masterdata.PartnerDataAccess;
import com.appnexus.masterdata.domain.geography.GeographyHierarchy;
import com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration;
import com.appnexus.masterdata.bootstrap.MasterDataConfiguration;
import com.appnexus.masterdata.application.masterdata.MasterDataService;

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
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

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
      try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V87__partner_profile_details.sql"));}
      try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V90__warehouse_profile.sql"));}
      try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V92__international_subdivisions.sql"));}
      try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V107__editable_geography_parent.sql"));}
      try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V118__storage_hierarchy.sql"));}
      try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V122__partner_data_ownership.sql"));
        ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V150__warehouse_organization_ownership.sql"));
        ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V176__master_data_status_history.sql"));}
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission",Integer.class)).isEqualTo(36);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_role_permission",Integer.class)).isEqualTo(36);
      var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
      var other=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
      var revoked=new java.util.concurrent.atomic.AtomicBoolean(false);
      AccessAuthorization access=(subject,permission)->!revoked.get()&&jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=?",Integer.class,permission)==1;
      var catalog=org.mockito.Mockito.mock(com.appnexus.platform.configuration.BusinessValueCatalog.class);
      org.mockito.Mockito.when(catalog.reference(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyMap())).thenReturn(true);
      var planning=org.mockito.Mockito.mock(com.appnexus.platform.authorization.AccessPlanning.class);
      org.mockito.Mockito.when(planning.plan(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->new com.appnexus.platform.authorization.AccessPlan(call.getArgument(0),call.getArgument(1),call.getArgument(2),new com.appnexus.platform.authorization.DataScopeAuthorization.DataScope(com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ALL,java.util.Set.of()),Map.of(),java.time.Instant.now()));
      var fields=org.mockito.Mockito.mock(com.appnexus.platform.authorization.FieldProtection.class);
      UUID testOrganization=UUID.randomUUID();
      com.appnexus.platform.authorization.DataAccessReferences references=(subject,permission)->List.of(new com.appnexus.platform.authorization.DataAccessReferences.Organization(testOrganization,"隔离测试组织"));
      var dataCatalog=com.appnexus.platform.authorization.DataAccessCatalog.fromObjects(List.of(new BusinessPartnerDeclaration()));
      // 固定 Mapper 使用同一数据源，事务仍与原授权及审计写入保持一致。
      var factory=new org.mybatis.spring.SqlSessionFactoryBean();factory.setDataSource(source);
      var config=new org.apache.ibatis.session.Configuration();config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);config.setCacheEnabled(false);
      config.setDefaultScriptingLanguage(com.appnexus.platform.persistence.ScopedQueryLanguageDriver.class);config.setCallSettersOnNulls(true);
      for(Class<?> type:List.of(com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper.class,com.appnexus.masterdata.adapter.persistence.MasterRecordMapper.class,com.appnexus.masterdata.adapter.persistence.MasterReadMapper.class,com.appnexus.masterdata.adapter.persistence.GeographyMapper.class,com.appnexus.masterdata.adapter.persistence.StorageHierarchyMapper.class,com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper.class))config.addMapper(type);
      factory.setConfiguration(config);var session=new org.mybatis.spring.SqlSessionTemplate(factory.getObject());
      var records=session.getMapper(com.appnexus.masterdata.adapter.persistence.MasterRecordMapper.class);var reads=session.getMapper(com.appnexus.masterdata.adapter.persistence.MasterReadMapper.class);var geography=session.getMapper(com.appnexus.masterdata.adapter.persistence.GeographyMapper.class);var storage=session.getMapper(com.appnexus.masterdata.adapter.persistence.StorageHierarchyMapper.class);var details=session.getMapper(com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper.class);
      var rowAccess=new com.appnexus.platform.persistence.platformscope.MySqlRowAccessControl();
      var service=MasterDataConfiguration.create(records,reads,geography,storage,details,access,new DataSourceTransactionManager(source),catalog,new PartnerDataAccess(planning,fields,references,reads,dataCatalog,rowAccess,details,org.mockito.Mockito.mock(com.appnexus.platform.authorization.ScopedWriteGate.class)),fields,dataCatalog,planning,rowAccess,references,org.mockito.Mockito.mock(com.appnexus.platform.authorization.ScopedWriteGate.class),session.getMapper(com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper.class));
      verifyStorageHierarchy(service,jdbc);
      for(var kind:Kind.values()){
        // 通用档案测试先前已停用上级；恢复父级后才能验证下一级的独立写入。
        if(kind==Kind.STORAGE_AREA)jdbc.update("UPDATE md_warehouse SET enabled=TRUE WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString());
        if(kind==Kind.STORAGE_LOCATION)jdbc.update("UPDATE md_storage_area SET enabled=TRUE WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString());
        Map<String,String> attrs=switch(kind){
          case PARTNER->Map.of("partnerType","BOTH");
          case STORAGE_AREA,STORAGE_LOCATION->Map.of("parentCode","CODE-1");
          case COUNTRY_REGION->Map.of("alpha3Code","CHN","numericCode","156");
          default->Map.of();
        };
        var input=new Input(kind==Kind.COUNTRY_REGION?"CN":kind==Kind.SUBDIVISION?"US-CA":"CODE-1","原始资料",attrs,0,null,null,kind==Kind.PARTNER?testOrganization.toString():null);
        String id=service.create(c,kind,input).get("id");
        assertThat(service.list(c,kind,"",null,1,20).total()).isEqualTo(1);
        assertThat(service.list(other,kind,"",null,1,20).total()).isZero();
        if(kind==Kind.SUBDIVISION){
          assertThat(service.list(c,kind,"",null,1,20,null,"continent:NA").rows().getFirst().attributes()).containsEntry("hierarchyCode","NA-US-CA").containsEntry("parentHierarchyCode","NA-US");
          assertThat(service.list(c,kind,"NA-US-CA",true,1,20,null,"US").total()).isEqualTo(1);
          assertThat(service.list(c,kind,"NA-US-CA",true,1,20,null,"CN").total()).isZero();
          assertThat(service.list(c,kind,"",null,1,20,null,"CN").total()).isZero();
          assertThat(service.list(other,kind,"",null,1,20,null,"US").total()).isZero();
        }
        if(kind==Kind.COUNTRY_REGION){
          assertThat(service.list(c,kind,"",null,1,20,null,"continent:AS").rows().getFirst().attributes()).containsEntry("hierarchyCode","AS-CN").containsEntry("parentHierarchyCode","AS");
          assertThat(service.list(c,kind,"",null,1,20,null,"continent:EU").total()).isZero();
        }
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
        var history = session.getMapper(com.appnexus.masterdata.adapter.persistence.MasterStatusHistoryMapper.class);
        assertThat(history.list(c.tenantId().toString(), kind.objectType, id, 0, 20))
          .singleElement().satisfies(event -> {
            assertThat(event.version()).isEqualTo(2);
            assertThat(event.beforeEnabled()).isTrue();
            assertThat(event.afterEnabled()).isFalse();
            assertThat(event.createdBy()).isEqualTo(c.accountId().toString());
          });
        assertThat(history.count(other.tenantId().toString(), kind.objectType, id)).isZero();
      }
      // 自定义节点、跨国移动和后代同步使用真实事务，拒绝循环及外租户父级后不推进审计。
      String customCountry=service.create(c,Kind.COUNTRY_REGION,new Input("CUSTOM_COUNTRY","自定义国别",Map.of("parentCode","continent:AS"),0)).get("id");
      String city=service.create(c,Kind.SUBDIVISION,new Input("LOCAL_CITY","本地城市",Map.of("parentCode","CN-FJ"),0)).get("id");
      String county=service.create(c,Kind.SUBDIVISION,new Input("LOCAL_COUNTY","本地县",Map.of("parentCode","LOCAL_CITY"),0)).get("id");
      var before=service.list(c,Kind.SUBDIVISION,"",null,1,1,city).rows().getFirst();
      assertThatThrownBy(()->service.update(c,Kind.SUBDIVISION,city,new Input("LOCAL_CITY","循环",Map.of("parentCode","LOCAL_COUNTY"),0))).isInstanceOf(Rejection.class);
      assertThat(service.list(c,Kind.SUBDIVISION,"",null,1,1,city).rows().getFirst().audit()).isEqualTo(before.audit());
      assertThatThrownBy(()->service.create(other,Kind.SUBDIVISION,new Input("CROSS","跨租户",Map.of("parentCode","LOCAL_CITY"),0))).isInstanceOf(Rejection.class);
      service.update(c,Kind.SUBDIVISION,city,new Input("LOCAL_CITY","已移动城市",Map.of("parentCode","CUSTOM_COUNTRY"),0));
      var child=service.list(c,Kind.SUBDIVISION,"",null,1,1,county).rows().getFirst();
      assertThat(child.attributes()).containsEntry("countryCode","CUSTOM_COUNTRY").containsEntry("parentHierarchyCode","AS-CUSTOM_COUNTRY-LOCAL_CITY").containsEntry("parentName","已移动城市");
      assertThat(child.version()).isEqualTo(1);assertThat(child.audit().updatedBy()).isEqualTo(c.accountId().toString());
      assertThat(service.list(c,Kind.SUBDIVISION,"",null,1,20,null,"CUSTOM_COUNTRY").rows()).extracting(Row::code).containsExactlyInAnyOrder("LOCAL_CITY","LOCAL_COUNTY");
      assertThat(service.list(c,Kind.SUBDIVISION,"LOCAL",null,1,20,null,"CN-FJ").total()).isZero();
      assertThatThrownBy(()->service.update(c,Kind.SUBDIVISION,county,new Input("LOCAL_COUNTY","过期",Map.of("parentCode","US"),0))).isInstanceOf(Rejection.class);
      assertThat(((GeographyHierarchy.Page)service.geographyTree(c,Kind.SUBDIVISION,"CUSTOM_COUNTRY",1,50)).rows()).extracting(GeographyHierarchy.Node::code).containsExactly("LOCAL_CITY");
      var summary=new com.appnexus.masterdata.adapter.persistence.masterdata.MyBatisGeographyCatalog(geography,
          org.mockito.Mockito.mock(com.appnexus.platform.authorization.ScopedWriteGate.class),new DataSourceTransactionManager(source)).nodes(c.tenantId());
      assertThat(summary.stream().filter(n->n.code().equals("LOCAL_CITY")).findFirst().orElseThrow().parentCode()).isEqualTo("CUSTOM_COUNTRY");
      service.update(c,Kind.COUNTRY_REGION,customCountry,new Input("CUSTOM_COUNTRY","移动国别",Map.of("parentCode","continent:EU"),0));
      assertThat(service.list(c,Kind.SUBDIVISION,"EU-CUSTOM_COUNTRY",null,1,20).total()).isEqualTo(2);
      // 明细作为聚合与父版本同事务保存；真实数据库证明审计不可伪造且外客商明细不能冒用。
      var contact=new DetailInput(null,Map.of("contactType","BUSINESS","contactName","测试联系人","phone","01000000000","address","测试地址","isDefault","true"));
      var bank=new DetailInput(null,Map.of("taxAccount","false","basicAccount","true","isDefault","true","bankName","测试银行","accountNumber","000123","currency","CNY"));
      String partner=service.create(c,Kind.PARTNER,new Input("DETAIL","聚合",Map.of("partnerType","CUSTOMER,CARGO_OWNER"),0,List.of(contact),List.of(bank),testOrganization.toString())).get("id");
      var profile=service.list(c,Kind.PARTNER,"",null,1,1,partner).rows().getFirst();
      assertThat(profile.contacts()).hasSize(1);assertThat(profile.bankAccounts()).hasSize(1);
      assertThat(profile.bankAccounts().getFirst().values().get("accountNumber")).isEqualTo("000123");
      assertThat(profile.contacts().getFirst().audit().createdBy()).isEqualTo(c.accountId().toString());
      assertThat(service.list(other,Kind.PARTNER,"",null,1,1,partner).rows()).isEmpty();
      var foreign=new DetailInput(UUID.randomUUID().toString(),contact.values());
      assertThatThrownBy(()->service.update(c,Kind.PARTNER,partner,new Input("DETAIL","不应提交",Map.of("partnerType","CUSTOMER"),0,List.of(foreign),List.of()))).isInstanceOf(Rejection.class);
      var unchanged=service.list(c,Kind.PARTNER,"",null,1,1,partner).rows().getFirst();
      assertThat(unchanged.version()).isZero();assertThat(unchanged.name()).isEqualTo("聚合");assertThat(unchanged.bankAccounts()).hasSize(1);
      service.update(c,Kind.PARTNER,partner,new Input("DETAIL","完成",Map.of("partnerType","CUSTOMER"),0,List.of(new DetailInput(profile.contacts().getFirst().id(),contact.values())),List.of()));
      var changed=service.list(c,Kind.PARTNER,"",null,1,1,partner).rows().getFirst();
      assertThat(changed.contacts().getFirst().audit()).isEqualTo(profile.contacts().getFirst().audit());
      assertThat(changed.bankAccounts()).isEmpty();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_partner_bank_account WHERE partner_id=UUID_TO_BIN(?) AND deleted=TRUE AND updated_by=UUID_TO_BIN(?)",Integer.class,partner,c.accountId().toString())).isEqualTo(1);assertThat(changed.audit().createdAt()).isEqualTo(profile.audit().createdAt());
      assertThatThrownBy(()->service.update(c,Kind.PARTNER,partner,new Input("DETAIL","陈旧",Map.of("partnerType","CUSTOMER"),0,List.of(),List.of()))).isInstanceOf(Rejection.class);
      assertThat(service.list(c,Kind.PARTNER,"",null,1,1,partner).rows().getFirst().contacts()).hasSize(1);
      revoked.set(true);
      assertThatThrownBy(()->service.list(c,Kind.PARTNER,"",null,1,20)).isInstanceOf(Rejection.class).hasMessage("MASTER_DATA_DENIED");
    }
  }
  /** 真实外键、事务及审计的正反向验证；使用独立租户，不污染通用档案回归数据。 */
  private void verifyStorageHierarchy(MasterDataService service,JdbcTemplate jdbc){
    var tenant=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    var foreign=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    String warehouse=service.create(tenant,Kind.WAREHOUSE,new Input("W","测试仓库",Map.of(),0)).get("id");
    var areaInput=new Input("A","测试库区",Map.of("parentCode","W","areaType","GOOD","capacity","0.125"),0);
    assertThatThrownBy(()->service.create(foreign,Kind.STORAGE_AREA,areaInput)).isInstanceOf(Rejection.class);
    String area=service.create(tenant,Kind.STORAGE_AREA,areaInput).get("id");
    String location=service.create(tenant,Kind.STORAGE_LOCATION,new Input("L","测试库位",Map.of("parentCode","A"),0)).get("id");
    assertThatThrownBy(()->service.create(tenant,Kind.STORAGE_LOCATION,new Input("BAD","错误层级",Map.of("parentCode","W"),0))).isInstanceOf(Rejection.class);
    var reference=new com.appnexus.masterdata.api.masterdata.MasterDataManagement.StoragePosition(
        warehouse,area,location,0,0,0);
    assertThat(service.resolveStoragePosition(tenant,reference)).isEqualTo(reference);
    assertThatThrownBy(()->service.resolveStoragePosition(foreign,reference)).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->service.resolveStoragePosition(tenant,
        new com.appnexus.masterdata.api.masterdata.MasterDataManagement.StoragePosition(warehouse,area,location,0,0,1)))
        .hasMessage("MASTER_DATA_CHANGED");
    var before=service.list(tenant,Kind.STORAGE_AREA,"",true,1,20,null,"W").rows().getFirst();
    assertThat(before.audit().createdBy()).isEqualTo(tenant.accountId().toString());
    assertThat(before.audit().createdAt()).isNotBlank();
    assertThat(service.list(tenant,Kind.STORAGE_LOCATION,"",true,1,20,null,"W").total()).isZero();
    assertThat(service.list(tenant,Kind.STORAGE_LOCATION,"",true,1,20,null,"A").total()).isEqualTo(1);
    assertThatThrownBy(()->service.setStatus(tenant,Kind.WAREHOUSE,warehouse,new Status(false,0))).hasMessage("STORAGE_ACTIVE_CHILDREN");
    assertThatThrownBy(()->service.setStatus(tenant,Kind.STORAGE_AREA,area,new Status(false,0))).hasMessage("STORAGE_ACTIVE_CHILDREN");
    assertThat(service.list(tenant,Kind.STORAGE_AREA,"",null,1,20).rows().getFirst().audit()).isEqualTo(before.audit());
    String otherWarehouse=service.create(tenant,Kind.WAREHOUSE,new Input("W2","另一仓库",Map.of(),0)).get("id");
    assertThatThrownBy(()->service.resolveStoragePosition(tenant,
        new com.appnexus.masterdata.api.masterdata.MasterDataManagement.StoragePosition(otherWarehouse,area,location,0,0,0)))
        .isInstanceOf(Rejection.class);
    assertThatThrownBy(()->service.update(tenant,Kind.STORAGE_AREA,area,new Input("A","禁止移动",Map.of("parentCode","W2"),0))).hasMessage("MASTER_DATA_CHANGED");
    service.setStatus(tenant,Kind.STORAGE_LOCATION,location,new Status(false,0));
    assertThatThrownBy(()->service.resolveStoragePosition(tenant,reference)).isInstanceOf(Rejection.class);
    service.setStatus(tenant,Kind.STORAGE_AREA,area,new Status(false,0));
    service.setStatus(tenant,Kind.WAREHOUSE,warehouse,new Status(false,0));
    assertThatThrownBy(()->service.setStatus(tenant,Kind.STORAGE_LOCATION,location,new Status(true,1))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->service.create(tenant,Kind.STORAGE_AREA,new Input("A2","停用父级",Map.of("parentCode","W"),0))).isInstanceOf(Rejection.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_storage_area WHERE tenant_id=UUID_TO_BIN(?)",Long.class,foreign.tenantId().toString())).isZero();
  }
}
