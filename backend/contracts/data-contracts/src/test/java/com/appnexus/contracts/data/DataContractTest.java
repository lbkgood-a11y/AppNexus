package com.appnexus.contracts.data;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
/** 验证执行边界拒绝错误类型、缺失与多余数据，嵌套路径不执行表达式。 */
class DataContractTest {
  private final DataContract schema=new DataContract("leave.approval",1,"WORKFLOW",List.of(new DataContract.Field("days",DataContract.Type.NUMBER,true,"天"),new DataContract.Field("requiresHr",DataContract.Type.BOOLEAN,true,"")));
  @Test void acceptsTypedData(){assertDoesNotThrow(()->schema.validate(Map.of("days",5,"requiresHr",true)));}
  @Test void rejectsStringNumber(){var error=assertThrows(DataContract.Violation.class,()->schema.validate(Map.of("days","5","requiresHr",true)));assertEquals("days",error.path());assertEquals("DATA_FIELD_TYPE_INVALID",error.getMessage());}
  @Test void rejectsMissingDecision(){assertEquals("requiresHr",assertThrows(DataContract.Violation.class,()->schema.validate(Map.of("days",5))).path());}
  @Test void rejectsSensitiveExtra(){assertEquals("reason",assertThrows(DataContract.Violation.class,()->schema.validate(Map.of("days",5,"requiresHr",true,"reason","secret"))).path());}
  @Test void supportsOptionalNestedFieldWithoutDefaults(){var nested=new DataContract("nested",1,"PRINT",List.of(new DataContract.Field("person.name",DataContract.Type.TEXT,false,"")));assertDoesNotThrow(()->nested.validate(Map.of()));assertDoesNotThrow(()->nested.validate(Map.of("person",Map.of("name","示例"))));assertThrows(DataContract.Violation.class,()->nested.validate(Map.of("person","invalid")));}
  @Test void projectsOnlyDeclaredFields(){
    var source=new HashMap<String,Object>(Map.of("days",5,"requiresHr",true,"reason","private"));
    var projected=schema.project(source);
    assertEquals(Map.of("days",5,"requiresHr",true),projected);
    assertEquals("private",source.get("reason"));
    assertThrows(UnsupportedOperationException.class,()->projected.put("reason","private"));
  }
  @Test void decodesAndEncodesTypedTransport(){
    var encoded=Map.of("days","5.25","requiresHr","false");
    var decoded=schema.decode(encoded);
    assertEquals(new java.math.BigDecimal("5.25"),decoded.get("days"));
    assertEquals(false,decoded.get("requiresHr"));
    assertEquals(encoded,schema.encode(decoded));
  }
  @Test void rejectsInvalidTransportWithoutDisclosingValues(){
    for(String value:List.of("TRUE","1","sensitive")){
      var error=assertThrows(DataContract.Violation.class,()->schema.decode(Map.of("days","5","requiresHr",value)));
      assertEquals("DATA_FIELD_TYPE_INVALID",error.getMessage());
      assertEquals("requiresHr",error.path());
    }
    assertThrows(DataContract.Violation.class,()->schema.decode(Map.of("days","NaN","requiresHr","true")));
    assertThrows(DataContract.Violation.class,()->schema.decode(Map.of("days","5","requiresHr","true","reason","private")));
    assertThrows(DataContract.Violation.class,()->schema.decode(Map.of("days","5")));
  }
  @Test void nestedProjectionIsDetachedAndDeeplyImmutable(){
    var nested=new DataContract("nested",1,"PRINT",List.of(new DataContract.Field("person.name",DataContract.Type.TEXT,true,""),new DataContract.Field("person.phone",DataContract.Type.TEXT,false,"")));
    var person=new HashMap<String,Object>(Map.of("name","示例","secret","private"));
    var projected=nested.project(Map.of("person",person));
    person.put("name","changed");
    assertEquals(Map.of("person",Map.of("name","示例")),projected);
    assertThrows(UnsupportedOperationException.class,()->((Map<?,?>)projected.get("person")).clear());
    assertEquals(Map.of("person.name","示例"),nested.encode(projected));
    assertEquals(projected,nested.decode(nested.encode(projected)));
  }
  @Test void rejectsScalarAndObjectPathCollision(){
    assertThrows(IllegalArgumentException.class,()->new DataContract("bad",1,"PRINT",List.of(new DataContract.Field("person",DataContract.Type.TEXT,false,""),new DataContract.Field("person.name",DataContract.Type.TEXT,false,""))));
  }
  @Test void projectionRejectsMalformedOptionalContainer(){
    var nested=new DataContract("nested",1,"PRINT",List.of(new DataContract.Field("person.name",DataContract.Type.TEXT,false,"")));
    assertThrows(DataContract.Violation.class,()->nested.project(Map.of("person","malformed")));
    assertEquals(Map.of(),nested.project(Map.of()));
  }
}
