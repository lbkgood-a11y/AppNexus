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
}
