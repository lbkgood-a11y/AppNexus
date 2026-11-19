package com.appnexus.masterdata.application.product;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.masterdata.api.product.ProductStructureData.Migration;
import com.appnexus.contracts.operation.OperationPreviewProof.Proof;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
/** 只读预检协调验证；退出项未确认不能签发证明，问题不能被错误解释为可执行。 */
class ProductPreviewServiceTest {
  private Migration target(){return new Migration(1L,1L,null,null,null,null,null,List.of(),List.of(),"测试",null);}
  /** 未确认真实退出项时不签发token。 */
  @Test void unconfirmedDropsDoNotIssueProof(){
    var port=mock(ProductPreviewPort.class);when(port.source()).thenReturn(Map.of("status","DRAFT"));
    when(port.dropped(any())).thenReturn(List.of("item"));
    assertThatThrownBy(()->new ProductPreviewService(port).preview(target())).hasMessage("PRODUCT_DROPPED_VALUES_UNCONFIRMED");
    verify(port,never()).proof(any());
  }
  /** 启用态完整性问题必须标为不可执行，允许展示并不产生执行授权。 */
  @Test void enabledIncompleteTargetIsNotExecutable(){
    var port=mock(ProductPreviewPort.class);when(port.source()).thenReturn(Map.of("status","ENABLED","version",1L,"structureRevision",1L));
    when(port.issues(any())).thenReturn(List.of("unit"));when(port.proof(any())).thenReturn(new Proof("test-only",Instant.EPOCH));
    var result=new ProductPreviewService(port).preview(target());
    assertThat(result).containsEntry("executable",false).containsEntry("completionIssues",List.of("unit"));
    var order=inOrder(port);order.verify(port).source();order.verify(port).validate(any());order.verify(port).issues(any());order.verify(port).dropped(any());order.verify(port).proof(any());
  }
}
