package com.appnexus.platform;
import com.appnexus.masterdata.application.masterdata.MasterDataService;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.configuration.*;
import com.appnexus.platform.persistence.configuration.BusinessValueDefaults;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
/** 真实数据库验证业务字典预置、租户隔离、动态新增、停用保留、引用删除保护与历史提炼。 */
@SpringBootTest @ActiveProfiles("local")
class BusinessValueDictionaryTest extends DatabaseTestSupport {
 @Autowired JdbcTemplate jdbc;
 @Autowired BusinessValueCatalog catalog;
 @Autowired GeographyCatalog geography;
 @Autowired com.appnexus.masterdata.organization.PersonnelDirectory directory;
 @Autowired DictionaryManagement management;
 @Autowired MasterDataService master;
 @Autowired com.appnexus.masterdata.application.product.ProductCatalogService productCatalog;
 @Autowired com.appnexus.masterdata.application.product.ProductService products;
 @Autowired PlatformTransactionManager manager;
 @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;
 private VerifiedTenantContext tenant(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),member=UUID.randomUUID();
  jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'字典独立测试租户')",tenant.toString());
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'字典测试账号','unused')",actor.toString(),"dict-"+actor);
  jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),actor.toString());
  DictionaryFixtures.seed(jdbc,tenant,actor);bootstrap.initialize(tenant,member);
  return new VerifiedTenantContext(actor,tenant,member,1);
 }
 @org.junit.jupiter.api.AfterEach void clearRequest(){org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
 private void request(VerifiedTenantContext c){var request=new org.springframework.mock.web.MockHttpServletRequest();request.setAttribute(VerifiedTenantContext.class.getName(),c);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));}
 /** 真实 MySQL 八并发测量，单轮解析 600 个字典、地理、组织及岗位引用。 */
 @Test void catalogNameBatchesHaveBoundedQueriesUnderConcurrentReads() throws Exception {
  var c=tenant();
  String tenant=c.tenantId().toString(),actor=c.accountId().toString(),org=organization(c);
  String organizationType=jdbc.queryForObject("SELECT BIN_TO_UUID(type_id) FROM tenant_organization_unit WHERE id=UUID_TO_BIN(?)",String.class,org);
  String dictionaryId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code='partner_type'",String.class,tenant);
  var dictionaryRecords=new ArrayList<Object[]>();
  var geographyRecords=new ArrayList<Object[]>();
  var organizationRecords=new ArrayList<Object[]>();
  var positionRecords=new ArrayList<Object[]>();
  var dictionaryKeys=new ArrayList<BusinessValueCatalog.NameKey>();
  var geographyKeys=new ArrayList<String>();
  var organizationKeys=new ArrayList<com.appnexus.masterdata.organization.PersonnelDirectory.OrganizationNameKey>();
  for(int i=0;i<200;i++){
    String id=UUID.randomUUID().toString(),code="NAME_"+i,lower="name_"+i;
    dictionaryRecords.add(new Object[]{UUID.randomUUID().toString(),tenant,dictionaryId,lower,code,code,actor,actor});
    geographyRecords.add(new Object[]{UUID.randomUUID().toString(),tenant,code,code});
    dictionaryKeys.add(new BusinessValueCatalog.NameKey("partner_type",code));
    geographyKeys.add(code);
    if(i<100){
      organizationRecords.add(new Object[]{tenant,id,code,organizationType,code});
      organizationKeys.add(new com.appnexus.masterdata.organization.PersonnelDirectory.OrganizationNameKey("organization",id));
    }else{
      positionRecords.add(new Object[]{tenant,id,org,code,code});
      organizationKeys.add(new com.appnexus.masterdata.organization.PersonnelDirectory.OrganizationNameKey("position",id));
    }
  }
  jdbc.batchUpdate("""
      INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,created_by,updated_by)
      VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))
      """,dictionaryRecords);
  jdbc.batchUpdate("INSERT INTO md_country_region(id,tenant_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",geographyRecords);
  jdbc.batchUpdate("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),?)",organizationRecords);
  jdbc.batchUpdate("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",positionRecords);
  var transaction=new TransactionTemplate(manager);
  var other=tenant();
  assertThat(catalog.names(other.tenantId(),dictionaryKeys)).isEmpty();
  assertThat(geography.names(other.tenantId(),geographyKeys)).isEmpty();
  assertThat(directory.organizationReferenceNames(other,organizationKeys)).isEmpty();
  java.util.concurrent.Callable<long[]> read=()->transaction.execute(status->{
    long before=selectCount(),start=System.nanoTime();
    assertThat(catalog.names(c.tenantId(),dictionaryKeys)).hasSize(200);
    assertThat(geography.names(c.tenantId(),geographyKeys)).hasSize(200);
    assertThat(directory.organizationReferenceNames(c,organizationKeys)).hasSize(200);
    return new long[]{(System.nanoTime()-start)/1_000_000,selectCount()-before};
  });
  long[] warm=read.call();
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)){
    var samples=pool.invokeAll(Collections.nCopies(48,read),30,java.util.concurrent.TimeUnit.SECONDS);
    var timings=new ArrayList<Long>();
    for(var sample:samples){
      long[] result=sample.get();
      assertThat(result[1]).isEqualTo(warm[1]).isLessThan(30);
      timings.add(result[0]);
    }
    timings.sort(Long::compareTo);
    long p95=timings.get((int)Math.ceil(timings.size()*0.95)-1);
    System.out.println("Catalog names: 600 references, 8 workers, 48 samples, "+warm[1]+" SELECT/request, p95 "+p95+" ms");
  }
 }

 private long selectCount(){
  return Objects.requireNonNull(jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'",(row,index)->row.getLong("Value")));
 }

 @Test void configurableValuesRespectStatusReferencesAndTenant(){
  var c=tenant();request(c);var context=new DictionaryManagement.Context(c.accountId(),c.tenantId(),c.memberId());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,c.tenantId().toString())).isEqualTo(25);
  var dictionary=management.dictionaries(context).stream().filter(row->row.code().equals("partner_type")).findFirst().orElseThrow();
  var option=management.createItem(context,dictionary.id(),new DictionaryManagement.CreateItem(null,"custom_role","CUSTOM_ROLE","自定义客商类型",null,1));
  var productUnit=productCatalog.createUnit(c,new com.appnexus.masterdata.api.product.ProductCatalogData.UnitInput("PIECE","件","COUNT",0,0));
  // 商品与客商各自执行归属校验，让无效单位断言实际抵达引用检查。
  String organization=organization(c);
  products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input(
      "UNIT","商品单位",null,null,null,"PHYSICAL",productUnit.id(),null,null,null,null,"",organization));
  assertThatThrownBy(()->products.create(c,new com.appnexus.masterdata.api.product.ProductMainData.Input(
      "BADUNIT","非法商品单位",null,null,null,"PHYSICAL",UUID.randomUUID().toString(),null,null,null,null,"",organization)))
      .isInstanceOf(MasterData.Rejection.class).hasMessage("PRODUCT_V1_INPUT_INVALID");
  var attributes=Map.of("partnerType","CUSTOM_ROLE","nature","PRIVATE");
  String id=master.create(c,MasterData.Kind.PARTNER,new MasterData.Input("DYNAMIC","动态字典客商",attributes,0,null,null,organization)).get("id");
  assertThat(catalog.options(c.tenantId(),BusinessValueCatalog.PARTNER).get("partnerType")).anyMatch(row->row.value().equals("CUSTOM_ROLE")&&row.enabled());
  management.updateItem(context,dictionary.id(),option.id(),new DictionaryManagement.UpdateItem(null,"重命名类型",null,DictionaryManagement.Status.DISABLED,1,option.version()));
  DictionaryFixtures.seed(jdbc,c.tenantId(),null);
  assertThat(catalog.options(c.tenantId(),BusinessValueCatalog.PARTNER).get("partnerType")).anyMatch(row->row.value().equals("CUSTOM_ROLE")&&!row.enabled()&&row.name().equals("重命名类型"));
  assertThatThrownBy(()->master.create(c,MasterData.Kind.PARTNER,new MasterData.Input("DENIED","停用新选",attributes,0,null,null,organization))).isInstanceOf(MasterData.Rejection.class);
  master.update(c,MasterData.Kind.PARTNER,id,new MasterData.Input("DYNAMIC","保留历史停用值",attributes,0));
  assertThatThrownBy(()->management.deleteItem(context,dictionary.id(),option.id(),1)).hasMessageContaining("字典项已被业务引用");
  org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
  var other=tenant();
  request(other);
  String otherOrganization=organization(other);
  assertThatThrownBy(()->master.create(other,MasterData.Kind.PARTNER,
      new MasterData.Input("CROSS","跨租户非法值",attributes,0,null,null,otherOrganization)))
      .isInstanceOf(MasterData.Rejection.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND code='DENIED'",
      Integer.class,c.tenantId().toString())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND code='CROSS'",
      Integer.class,other.tenantId().toString())).isZero();
 }
 /** 为字典引用测试创建实际租户内组织，防止缺少必填归属使拒绝断言误通过。 */
 private String organization(VerifiedTenantContext c){
  String type=jdbc.query("SELECT BIN_TO_UUID(id) FROM tenant_organization_type WHERE tenant_id=UUID_TO_BIN(?) AND code='BUSINESS'",r->r.next()?r.getString(1):null,c.tenantId().toString());
  if(type==null){type=UUID.randomUUID().toString();jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务组织')",c.tenantId().toString(),type);}
  String id=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,UUID_TO_BIN(?),'字典引用测试组织')",c.tenantId().toString(),id,"DICT-"+id,type);
  return id;
 }
 @Test void legacyTextBecomesAnAuditedReferencedDictionaryValue(){
  var c=tenant();String id=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO md_partner(id,tenant_id,code,name,partner_type,nature) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'LEGACY','历史测试','BOTH','历史特殊性质')",id,c.tenantId().toString());
  new TransactionTemplate(manager).executeWithoutResult(status->BusinessValueDefaults.legacy(MapperFixtures.mapper(jdbc,com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class),c.tenantId()));
  assertThat(catalog.options(c.tenantId(),BusinessValueCatalog.PARTNER).get("nature")).anyMatch(row->row.value().equals("历史特殊性质"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_dictionary_item_reference WHERE tenant_id=UUID_TO_BIN(?) AND reference_key=?",Integer.class,c.tenantId().toString(),id)).isGreaterThanOrEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT nature FROM md_partner WHERE id=UUID_TO_BIN(?)",String.class,id)).isEqualTo("历史特殊性质");
 }
}
