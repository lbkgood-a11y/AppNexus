package com.appnexus.platform.businessobject.internal.application.execution;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.businessobject.port.ObjectRecordStore;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.appnexus.contracts.businessobject.ObjectRecordMapping.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 固定映射在装配时验证声明完整性，防止未声明操作或缺失字段进入执行路径。 */
class MappedObjectRuntimeTest {
  final Provider provider=()->new Descriptor("MappedExample","映射对象","Mapped example","test",1,Category.TRANSACTION,List.of(),List.of(
    new Operation("submit","提交","Submit","test:submit","MappedExample.submit",null,List.of(),new InteractionContract("MappedExample.submit",1,"MappedExample","submit","提交","Submit",InteractionContract.Trigger.USER_ACTION,List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,"MappedExample.submit","",List.of(new Field("label","名称","Label","TEXT","",true)),Set.of(),"mapped.data",1))))));
  ObjectRecordMapping mapping(Map<String,Value> fields,Map<String,Transition> transitions){var text=Encoding.TEXT;return new ObjectRecordMapping("test_record",new Column("tenant_id",text),new Column("id",text),new Column("organization_id",text),new Column("applicant",text),"version","state","instance_id","delivery_id",null,fields,transitions,null,false);}
  @Test void undeclaredOperationAndMissingFieldFailAtAssembly(){
    var store=mock(ObjectRecordStore.class);var fields=Map.of("label",new Value(new Column("label",Encoding.TEXT),Transform.VALUE,0));
    assertThatThrownBy(()->new MappedObjectRuntime(provider,mapping(fields,Map.of("delete",new Transition(Set.of("DRAFT"),"DELETED",false))),store,(c,id,op,s)->{})).hasMessage("OBJECT_OPERATION_MAPPING_UNDECLARED");
    assertThatThrownBy(()->new MappedObjectRuntime(provider,mapping(Map.of(),Map.of("submit",new Transition(Set.of("DRAFT"),"ACCEPTED",false))),store,(c,id,op,s)->{})).hasMessage("OBJECT_FIELD_MAPPING_MISSING");
    verifyNoInteractions(store);
  }
  @Test void personRequiredCannotOmitPersonColumn(){assertThatThrownBy(()->mapping(Map.of(),Map.of("submit",new Transition(Set.of("DRAFT"),"ACCEPTED",true)))).hasMessage("OBJECT_PERSON_MAPPING_REQUIRED");}
  @Test void rejectsSqlInEveryIdentityColumn(){assertThatThrownBy(()->new Column("id OR 1=1",Encoding.TEXT)).hasMessage("OBJECT_FIXED_IDENTIFIER_REQUIRED");}
}
