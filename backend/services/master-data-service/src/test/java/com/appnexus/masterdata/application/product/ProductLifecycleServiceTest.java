package com.appnexus.masterdata.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 商品状态用例测试，拒绝草稿停用、审批冻结后不写及启用完整性顺序。 */
class ProductLifecycleServiceTest {
  private final VerifiedTenantContext context=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
  private final String id=UUID.randomUUID().toString();
  /** 草稿不能停用；冻结拒绝必须先于状态写入和审计。 */
  @Test void rejectedTransitionsNeverWrite(){
    var port=mock(ProductLifecyclePort.class);var service=new ProductLifecycleService(port,(s,p)->true);
    when(port.lock(context,id,1)).thenReturn(new ProductLifecyclePort.State("DRAFT",false,"COMPLETE"));
    assertThatThrownBy(()->service.status(context,id,1,"DISABLED")).hasMessage("PRODUCT_V1_CHANGED");
    verify(port,never()).status(any(),anyString(),anyString());verify(port,never()).commit(any(),anyString(),anyLong(),anyString());
  }
  /** 启用先校验审批和完整性，成功才写状态和事件。 */
  @Test void approvalAndCompletenessPrecedeStatusWrite(){
    var port=mock(ProductLifecyclePort.class);var service=new ProductLifecycleService(port,(s,p)->true);
    when(port.lock(context,id,1)).thenReturn(new ProductLifecyclePort.State("DRAFT",false,"COMPLETE"));
    service.status(context,id,1,"ENABLED");
    var order=inOrder(port);order.verify(port).lock(context,id,1);order.verify(port).requireUnfrozen(context,id);
    order.verify(port).requireApproved(context,id,1);order.verify(port).requireComplete(context,id);order.verify(port).status(context,id,"ENABLED");order.verify(port).commit(context,id,1,"STATUS");
  }
}
