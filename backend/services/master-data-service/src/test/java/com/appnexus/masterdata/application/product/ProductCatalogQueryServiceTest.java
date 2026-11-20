package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.application.product.ProductCatalogQueries;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
/** 无读取权限不得查询候选、配置或探测目录存在性。 */
class ProductCatalogQueryServiceTest {
  /** 三种读取路径均先授权，输出端口不得被调用。 */
  @Test void deniedReadersNeverReachCatalog(){
    var reads=mock(ProductCatalogQueries.class);var service=new ProductCatalogQueryService(reads,(s,p)->false);
    var c=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
    assertThatThrownBy(()->service.options(c,"category","",1,20)).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.configurations(c,"id",1,20)).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->service.configuration(c,"id")).hasMessage("MASTER_DATA_DENIED");
    verifyNoInteractions(reads);
  }
}
