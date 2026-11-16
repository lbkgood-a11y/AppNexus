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
  @Test void validatesAllFiveKindsAndRejectsUnknownFields(){
    for(var kind:Kind.values()){
      var fields=new HashMap<String,String>();
      if(kind==Kind.PARTNER)fields.put("partnerType","BOTH");
      if(kind==Kind.PRODUCT)fields.put("unit","件");
      if(kind==Kind.COUNTRY_REGION){fields.put("alpha3Code","CHN");fields.put("numericCode","156");}
      var input=new Input(kind==Kind.COUNTRY_REGION?"CN":"TEST-1","样本",fields,0);
      assertThat(MasterDataService.validate(kind,input)).hasSize(kind.fields.size());
      fields.put("tenantId","forged");
      assertThatThrownBy(()->MasterDataService.validate(kind,new Input(input.code(),input.name(),fields,0))).isInstanceOf(Rejection.class);
    }
  }
  @Test void rejectsInvalidCountryAndMissingUnit(){
    assertThatThrownBy(()->MasterDataService.validate(Kind.COUNTRY_REGION,new Input("cn","中国",Map.of("alpha3Code","CHN","numericCode","156"),0))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->MasterDataService.validate(Kind.PRODUCT,new Input("P1","商品",Map.of(),0))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->MasterDataService.validate(Kind.PARTNER,new Input("P1","客商",Map.of("partnerType","ADMIN"),0))).isInstanceOf(Rejection.class);
  }
  @Test void revokedOrMissingPermissionNeverTouchesDatabase(){
    var jdbc=mock(JdbcTemplate.class);var access=mock(AccessAuthorization.class);
    var service=new MasterDataService(jdbc,access,mock(PlatformTransactionManager.class));
    var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    for(var kind:Kind.values()){
      assertThatThrownBy(()->service.list(c,kind,"",null,1,20)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.create(c,kind,null)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.update(c,kind,"invalid",null)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.setStatus(c,kind,"invalid",null)).isInstanceOf(Rejection.class);
    }
    verifyNoInteractions(jdbc);
  }
}
