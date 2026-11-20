package com.appnexus.platform;
import com.appnexus.masterdata.application.masterdata.MasterDataService;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.configuration.*;
import com.appnexus.masterdata.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
/** 隔离MySQL验证仓库迁移、字典引用、停用保留、租户边界及失败不推进审计。 */
@SpringBootTest @ActiveProfiles("local")
class WarehouseDictionaryIntegrationTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired MasterDataService master;
 @Autowired DictionaryManagement dictionaries;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 private VerifiedTenantContext tenant(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),member=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'仓库测试租户')",tenant.toString());
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'仓库测试账号','unused')",actor.toString(),"warehouse-"+actor);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),actor.toString());
  DictionaryFixtures.seed(jdbc,tenant,actor);bootstrap.initialize(tenant,member);
  return new VerifiedTenantContext(actor,tenant,member,1);
 }
 private void request(VerifiedTenantContext c){var r=new org.springframework.mock.web.MockHttpServletRequest();r.setAttribute(VerifiedTenantContext.class.getName(),c);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(r));}
 @Test void dictionaryLifecycleAndIsolation(){
  var c=tenant();request(c);
  try{
   var context=new DictionaryManagement.Context(c.accountId(),c.tenantId(),c.memberId());
   var dictionary=dictionaries.dictionaries(context).stream().filter(row->row.code().equals("warehouse_type")).findFirst().orElseThrow();
   var item=dictionaries.createItem(context,dictionary.id(),new DictionaryManagement.CreateItem(null,"custom_warehouse","CUSTOM","自定义仓库",null,1));
   var values=Map.of("warehouseType","CUSTOM","operatingMode","SELF_OPERATED","capacity","400000.123456","longitude","118.191368","latitude","24.474135","wholeLease","true");
   String id=master.create(c,MasterData.Kind.WAREHOUSE,new MasterData.Input("W1","仓库",values,0)).get("id");
   var before=master.list(c,MasterData.Kind.WAREHOUSE,"",null,1,1,id).rows().getFirst();
   assertThat(before.attributes()).containsAllEntriesOf(values);assertThat(before.audit().createdBy()).isEqualToIgnoringCase(c.accountId().toString());
   dictionaries.updateItem(context,dictionary.id(),item.id(),new DictionaryManagement.UpdateItem(null,"停用仓库类型",null,DictionaryManagement.Status.DISABLED,1,item.version()));
   assertThatThrownBy(()->master.create(c,MasterData.Kind.WAREHOUSE,new MasterData.Input("BAD","停用新选",values,0))).isInstanceOf(MasterData.Rejection.class);
   master.update(c,MasterData.Kind.WAREHOUSE,id,new MasterData.Input("W1","保留历史",values,0));
   var after=master.list(c,MasterData.Kind.WAREHOUSE,"",null,1,1,id).rows().getFirst();
   assertThat(after.audit().createdAt()).isEqualTo(before.audit().createdAt());assertThat(after.version()).isEqualTo(1);
   assertThatThrownBy(()->master.update(c,MasterData.Kind.WAREHOUSE,id,new MasterData.Input("W1","过期覆盖",values,0))).isInstanceOf(MasterData.Rejection.class);
   assertThat(master.list(c,MasterData.Kind.WAREHOUSE,"",null,1,1,id).rows().getFirst().audit()).isEqualTo(after.audit());
   assertThatThrownBy(()->dictionaries.deleteItem(context,dictionary.id(),item.id(),1)).hasMessageContaining("字典项已被业务引用");
   var other=tenant();request(other);
   assertThat(master.list(other,MasterData.Kind.WAREHOUSE,"",null,1,1,id).rows()).isEmpty();
   assertThatThrownBy(()->master.update(other,MasterData.Kind.WAREHOUSE,id,new MasterData.Input("W1","跨租户",values,1))).isInstanceOf(MasterData.Rejection.class);
   assertThatThrownBy(()->master.create(other,MasterData.Kind.WAREHOUSE,new MasterData.Input("CROSS","跨租户类型",values,0))).isInstanceOf(MasterData.Rejection.class);
  }finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
 }
}
