package com.appnexus.masterdata.application.personnel;
import com.appnexus.masterdata.application.personnel.PersonDataService;
import com.appnexus.masterdata.organization.PersonData;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.*;
import com.appnexus.masterdata.organization.port.personnel.PersonRepository;
import com.appnexus.masterdata.organization.port.personnel.PersonnelTransaction;
import static com.appnexus.masterdata.organization.PersonData.*;

/** 输入、独立权限及失败关闭测试；真实 MySQL 租户和并发测试在集成测试中执行。 */
class PersonDataServiceTest {
  @Test void validatesPersonnelFieldsAndRejectsUnknownFields(){
    for(var kind:Kind.values()){
      var fields=new HashMap<String,String>();
      var input=new Input("TEST-1","人员",fields,0);
      assertThat(PersonDataService.validate(kind,input)).hasSize(kind.fields.size());
      fields.put("tenantId","forged");
      assertThatThrownBy(()->PersonDataService.validate(kind,new Input(input.code(),input.name(),fields,0))).isInstanceOf(Rejection.class);
    }
  }
  @Test void rejectsInvalidPersonnelEmailAndFutureBirth(){
    assertThatThrownBy(()->PersonDataService.validate(Kind.PERSON,new Input("P1","人员",Map.of("email","invalid"),0))).isInstanceOf(Rejection.class);
    assertThatThrownBy(()->PersonDataService.validate(Kind.PERSON,new Input("P1","人员",Map.of("birthDate","9999-01-01"),0))).isInstanceOf(Rejection.class);
  }
  @Test void revokedOrMissingPermissionNeverTouchesDatabase(){
    var records=mock(PersonRepository.class);var access=mock(AccessAuthorization.class);
    var service=new PersonDataService(records,access,mock(PersonnelTransaction.class),mock(com.appnexus.platform.configuration.BusinessValueCatalog.class));
    var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    for(var kind:Kind.values()){
      assertThatThrownBy(()->service.list(c,kind,"",null,1,20)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.create(c,kind,null)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.update(c,kind,"invalid",null)).isInstanceOf(Rejection.class);
      assertThatThrownBy(()->service.setStatus(c,kind,"invalid",null)).isInstanceOf(Rejection.class);
    }
    verifyNoInteractions(records);
  }
}
