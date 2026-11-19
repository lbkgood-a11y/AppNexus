package com.appnexus.masterdata;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static com.appnexus.masterdata.MasterData.*;

/** 输入、独立权限及失败关闭测试；真实 MySQL 租户和并发测试在集成测试中执行。 */
class MasterDataServiceTest {
  /** 宿主读取权是地理选择的边界；无权限时不得先查询目录。 */
  @Test void geographyDirectoryRequiresHostReadPermission(){
    var jdbc=mock(JdbcTemplate.class);var access=mock(AccessAuthorization.class);
    var service=MasterDataConfiguration.create(jdbc,access,mock(PlatformTransactionManager.class),mock(com.appnexus.platform.configuration.BusinessValueCatalog.class),null);
    var context=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    assertThatThrownBy(()->service.geographyCatalog(context,Kind.PARTNER)).isInstanceOf(Rejection.class).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.geographyTree(context,Kind.SUBDIVISION,"",1,20)).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->service.list(context,Kind.SUBDIVISION,"",null,1,20,null,"CN-FJ")).isInstanceOf(Rejection.class);
    verifyNoInteractions(jdbc);
  }
  @Test void validatesAllKindsAndRejectsUnknownFields(){
    for(var kind:Kind.values()){
      var fields=new HashMap<String,String>();
      if(kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)fields.put("parentCode","PARENT");
      if(kind==Kind.PARTNER)fields.put("partnerType","BOTH");
      if(kind==Kind.COUNTRY_REGION){fields.put("alpha3Code","CHN");fields.put("numericCode","156");}
      var input=new Input(kind==Kind.COUNTRY_REGION?"CN":kind==Kind.SUBDIVISION?"US-CA":"TEST-1","样本",fields,0);
      assertThat(MasterDataService.validate(kind,input)).hasSize(kind.fields.size());
      fields.put("tenantId","forged");
      assertThatThrownBy(()->MasterDataService.validate(kind,new Input(input.code(),input.name(),fields,0))).isInstanceOf(Rejection.class);
    }
  }
  /** 格式合法但错配的编码拒绝；标准区划保留所属国别及可变层级。 */
  @Test void validatesInternationalStandards(){
    assertThat(MasterDataService.validate(Kind.COUNTRY_REGION,new Input("AF","阿富汗",Map.of(),0))).containsEntry("numericCode","004").containsEntry("alpha3Code","AFG");
    assertThatThrownBy(()->MasterDataService.validate(Kind.COUNTRY_REGION,new Input("CN","中国",Map.of("alpha3Code","USA","numericCode","156"),0))).isInstanceOf(Rejection.class);
    assertThatCode(()->MasterDataService.validate(Kind.COUNTRY_REGION,new Input("ZZ","自定义",Map.of("parentCode","continent:AS"),0))).doesNotThrowAnyException();
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("US-CA","加利福尼亚",Map.of(),0))).containsEntry("countryCode","US");
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("US-CA","自定义归属",Map.of("parentCode","CN"),0))).containsEntry("parentCode","CN");
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("BD-01","测试",Map.of(),0))).containsEntry("parentCode","BD-B");
    assertThat(GeographyStandards.entries(false)).hasSize(249);
    assertThat(GeographyStandards.entries(true)).hasSizeGreaterThan(5000);
  }
  @Test void rejectsInvalidCountryAndMissingUnit(){
    assertThatThrownBy(()->MasterDataService.validate(Kind.COUNTRY_REGION,new Input("custom","中国",Map.of("alpha3Code","INVALID","numericCode","156"),0))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",Map.of("partnerType","INVALID ROLE"),0))).isInstanceOf(Rejection.class);
  }
  /** 标准快照保留缺省属性；租户上级允许修改，自定义码由事务内租户图校验。 */
  @Test void validatesChinaOfficialHierarchy(){
    var entries=GeographyStandards.entries(true).stream().filter(e->e.code().startsWith("CN-MCA-")).toList();
    assertThat(entries).hasSize(3178);
    assertThat(entries.stream().filter(e->e.code().startsWith("CN-MCA-35"))).hasSize(93);
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("CN-MCA-350100","福州市",Map.of(),0))).containsEntry("parentCode","CN-FJ");
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("CN-MCA-350102","鼓楼区",Map.of(),0))).containsEntry("parentCode","CN-MCA-350100");
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("CN-MCA-110101","东城区",Map.of(),0))).containsEntry("parentCode","CN-BJ");
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("CN-MCA-350102","鼓楼区",Map.of("parentCode","CN-GD"),0))).containsEntry("parentCode","CN-GD");
    assertThat(MasterDataService.validate(Kind.SUBDIVISION,new Input("CN-MCA-350102","鼓楼区",Map.of("countryCode","US"),0))).containsEntry("countryCode","US");
    assertThatCode(()->MasterDataService.validate(Kind.SUBDIVISION,new Input("CUSTOM-FZ","自定义福州",Map.of("parentCode","CN-FJ"),0))).doesNotThrowAnyException();
  }
  /** 每条导入记录须与保留的官方年度响应一致，不因清单手工编辑造成名称或层级漂移。 */
  @Test void chinaDirectoryMatchesOfficialResponse() throws Exception {
    try(var stream=getClass().getResourceAsStream("/geography/cn-mca-2025-source.json")) {
      var source=new tools.jackson.databind.ObjectMapper().readTree(stream);
      assertThat(source.get("tableName").asText()).isEqualTo("Xzqh20251231");
      var directory=new HashMap<String,GeographyStandards.Entry>();
      GeographyStandards.entries(true).forEach(e->directory.put(e.code(),e));
      assertThat(source.get("rows").size()).isEqualTo(3178);
      for(var row:source.get("rows")) {
        String raw=row.get("code").asText();assertThat(raw).matches("[0-9]{6}000");
        var entry=directory.get("CN-MCA-"+raw.substring(0,6));
        assertThat(entry).isNotNull();assertThat(entry.name()).isEqualTo(row.get("name").asText());
        String parent=row.get("parentCode").asText().substring(0,6);
        if(!parent.endsWith("0000"))assertThat(entry.attributes().get("parentCode")).isEqualTo("CN-MCA-"+parent);
      }
    }
  }
  /** 金额、日期与多角色需服务端约束，不允许依赖浏览器校验。 */
  @Test void partnerProfileRules(){
    var fields=new HashMap<String,String>(Map.of("partnerType","CUSTOMER,CARGO_OWNER","registeredCapital","123.450000","registeredCurrency","CNY","establishedOn","2020-01-01"));
    assertThat(MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",fields,0))).containsEntry("registeredCapital","123.450000");
    for(String bad:List.of("-1","1e9","0.1234567")){fields.put("registeredCapital",bad);assertThatThrownBy(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",fields,0))).isInstanceOf(Rejection.class);}
    fields.put("registeredCapital","");fields.put("establishedOn","2026-02-30");
    assertThatThrownBy(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",fields,0))).isInstanceOf(Rejection.class);
    assertThat(PartnerDetails.validTypes("CUSTOMER,CUSTOMER")).isFalse();
    assertThat(PartnerDetails.validTypes("BOTH")).isTrue();
  }
  /** 默认项唯一、银行格式、未知字段和明细归属不能由页面自行放行。 */
  @Test void partnerDetailRules(){
    var values=new HashMap<String,String>(Map.of("contactType","BUSINESS","contactName","测试","phone","01000000000","address","测试地址","isDefault","true","email","test@example.invalid"));
    var row=new DetailInput(null,values);
    assertThatCode(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",Map.of("partnerType","CUSTOMER"),0,List.of(row),List.of()))).doesNotThrowAnyException();
    assertThatThrownBy(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",Map.of("partnerType","CUSTOMER"),0,List.of(row,row),List.of()))).isInstanceOf(Rejection.class);
    values.put("tenantId","forged");
    assertThatThrownBy(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",Map.of("partnerType","CUSTOMER"),0,List.of(row),List.of()))).isInstanceOf(Rejection.class);
  }
  /** 仓库数量、坐标、日期及布尔值在服务端校验，未知映射字段拒绝。 */
  @Test void warehouseProfileRules(){
    var fields=new HashMap<String,String>(Map.of("capacity","400000","totalArea","0.123456","longitude","118.191368","latitude","24.474135","builtOn","2020-02-29","wholeLease","true"));
    assertThatCode(()->MasterDataService.validate(Kind.WAREHOUSE,new Input("W1","仓库",fields,0))).doesNotThrowAnyException();
    for(var pair:List.of(Map.entry("capacity","-1"),Map.entry("totalArea","1e5"),Map.entry("latitude","90.000001"),Map.entry("longitude","181"),Map.entry("builtOn","2026-02-30"),Map.entry("wholeLease","yes"),Map.entry("email","invalid"),Map.entry("erpCode","forbidden"))){
      var bad=new HashMap<>(fields);bad.put(pair.getKey(),pair.getValue());assertThatThrownBy(()->MasterDataService.validate(Kind.WAREHOUSE,new Input("W1","仓库",bad,0))).isInstanceOf(Rejection.class);
    }
    fields.put("latitude","");assertThatThrownBy(()->MasterDataService.validate(Kind.WAREHOUSE,new Input("W1","仓库",fields,0))).isInstanceOf(Rejection.class);
  }
  @Test void revokedOrMissingPermissionNeverTouchesDatabase(){
    var jdbc=mock(JdbcTemplate.class);var access=mock(AccessAuthorization.class);
    var service=MasterDataConfiguration.create(jdbc,access,mock(PlatformTransactionManager.class),mock(com.appnexus.platform.configuration.BusinessValueCatalog.class),null);
    var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    for(var kind:Kind.values()){
      assertThatThrownBy(()->service.list(c,kind,"",null,1,20)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.create(c,kind,null)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.update(c,kind,"invalid",null)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.setStatus(c,kind,"invalid",null)).isInstanceOf(Rejection.class);
    }
    assertThatThrownBy(()->service.geographyTree(c,Kind.SUBDIVISION,"",1,20)).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->service.list(c,Kind.SUBDIVISION,"",null,1,20,null,"CN-FJ")).isInstanceOf(Rejection.class);
    verifyNoInteractions(jdbc);
  }
}
