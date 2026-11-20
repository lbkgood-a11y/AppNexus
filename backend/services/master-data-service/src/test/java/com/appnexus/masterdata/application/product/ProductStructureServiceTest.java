package com.appnexus.masterdata.application.product;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.masterdata.api.product.ProductStructureData.Migration;
import java.util.*;
import org.junit.jupiter.api.Test;
/** 结构迁移协调测试；成功重放不重验旧预览，首次证明失败不能写入。 */
class ProductStructureServiceTest {
  private Migration target(){return new Migration(1L,1L,null,null,null,null,null,List.of(),List.of(),"测试",null);}
  /** 相同请求重放直接返回，不重复读旧CAS或验证过期token。 */
  @Test void replayAvoidsProofAndWriting(){
    var port=mock(ProductStructurePort.class);when(port.replay()).thenReturn(Map.of("replayed",true));
    assertThat(new ProductStructureMigrationService(port).migrate(target())).containsEntry("replayed",true);
    verify(port,never()).source();verify(port,never()).apply(any());
  }
  /** 失效签名拒绝在任何迁移写入之前。 */
  @Test void invalidPreviewNeverChangesStructure(){
    var port=mock(ProductStructurePort.class);when(port.replay()).thenReturn(null);when(port.source()).thenReturn(Map.of("status","DRAFT"));
    assertThatThrownBy(()->new ProductStructureMigrationService(port).migrate(target())).hasMessage("PRODUCT_PREVIEW_CHANGED_OR_EXPIRED");
    verify(port,never()).apply(any());
  }
}
