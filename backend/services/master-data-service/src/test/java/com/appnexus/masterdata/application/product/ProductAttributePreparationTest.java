package com.appnexus.masterdata.application.product;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.masterdata.api.product.ProductMainData.Value;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
/** 复杂属性准备精度与选项隔离测试；无效值不能进入存储重写。 */
class ProductAttributePreparationTest {
  private final VerifiedTenantContext context=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
  /** 小数超声明精度拒绝，不使用浮点舍入掩盖错误。 */
  @Test void declaredScaleIsEnforced(){
    var port=mock(ProductAttributePreparationPort.class);String item=UUID.randomUUID().toString();
    when(port.definitions(context,"config",item)).thenReturn(List.of(Map.of("data_type","DECIMAL","revision","rev","numeric_scale",2,"validation","{}")));
    assertThatThrownBy(()->new ProductAttributePreparation(port).prepare(context,"config",List.of(new Value(item,"1.001",List.of())))).hasMessage("PRODUCT_V1_INPUT_INVALID");
    verify(port,never()).validateRule(anyString(),anyString(),any(),anyInt());
  }
  /** 其他修订或租户的选项计数为零时拒绝。 */
  @Test void foreignOrDisabledOptionIsRejected(){
    var port=mock(ProductAttributePreparationPort.class);String item=UUID.randomUUID().toString(),option=UUID.randomUUID().toString();
    when(port.definitions(context,"config",item)).thenReturn(List.of(Map.of("data_type","SINGLE_SELECT","revision","rev","validation","{}")));
    assertThatThrownBy(()->new ProductAttributePreparation(port).prepare(context,"config",List.of(new Value(item,null,List.of(option))))).hasMessage("PRODUCT_V1_INPUT_INVALID");
  }
}
