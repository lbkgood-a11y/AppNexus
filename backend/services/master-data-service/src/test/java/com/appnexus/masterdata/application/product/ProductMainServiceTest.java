package com.appnexus.masterdata.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 主档用例拒绝路径验证；数据库回滚另由真实商品流程覆盖。 */
class ProductMainServiceTest {
  private final VerifiedTenantContext context=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
  /** 无权创建不校验引用、不写任何数据，也不返回对象存在性。 */
  @Test void unauthorizedCreateDoesNotCallOutputPorts(){
    var port=mock(ProductMainPort.class);var service=new ProductMainService(port,(s,p)->false);
    assertThatThrownBy(()->service.create(context,new Input("CODE","名称",null,null,null,null,null,null,null,null,null))).hasMessage("MASTER_DATA_DENIED");
    verifyNoInteractions(port);
  }
  /** 启用记录修改后不完整，不能推进父版本和事件。 */
  @Test void incompleteEnabledProductNeverCommitsEvent(){
    var port=mock(ProductMainPort.class);var service=new ProductMainService(port,(s,p)->true);
    String id=UUID.randomUUID().toString();
    doAnswer(invocation->((Supplier<?>)invocation.getArgument(0)).get()).when(port).atomic(any());
    when(port.lock(context,id,1)).thenReturn(Map.of("status","ENABLED","structureState","COMPLETE"));
    when(port.completeness(context,id)).thenReturn(List.of("REQUIRED"));
    assertThatThrownBy(()->service.update(context,id,new Update("名称",null,null,null,null,null,1)))
        .hasMessage("PRODUCT_COMPLETION_REQUIRED");
    verify(port,never()).finish(any(),anyString(),any());
  }
}
