package com.appnexus.masterdata.domain.product;
import com.appnexus.masterdata.domain.product.ProductStructurePolicy.*;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 结构切换规则不依赖数据库；真实执行的锁、预检绑定和事务仍由商品 MySQL 工作流验证。 */
class ProductStructurePolicyTest {
  private Source source(String status,boolean pending,String legacy){return new Source(2,3,"old","PHYSICAL","unit","STANDARD",status,false,pending,legacy==null?"COMPLETE":"LEGACY_PENDING",legacy,Set.of("spec"));}
  private Change change(long version,Set<String> retired,String legacy){return new Change(version,3,"old","new","PHYSICAL","unit",null,retired,legacy);}

  @Test void sourceMustStillMatchAndApprovalFreezesEvenFreshChanges(){
    assertThatThrownBy(()->ProductStructurePolicy.requireAllowed(source("DISABLED",false,null),change(1,Set.of("spec"),null))).hasMessage("PRODUCT_SOURCE_VERSION_CHANGED");
    assertThatThrownBy(()->ProductStructurePolicy.requireAllowed(source("DISABLED",true,null),change(2,Set.of("spec"),null))).hasMessage("PRODUCT_APPROVAL_IN_PROGRESS");
  }
  @Test void structureChangeRequiresAllActiveSpecificationsToExit(){
    assertThatThrownBy(()->ProductStructurePolicy.requireAllowed(source("DISABLED",false,null),change(2,Set.of(),null))).hasMessage("PRODUCT_SPEC_EXIT_REQUIRED");
    assertThatCode(()->ProductStructurePolicy.requireAllowed(source("DISABLED",false,null),change(2,Set.of("spec"),null))).doesNotThrowAnyException();
  }
  @Test void enabledAndUnconfirmedLegacySourceCannotMigrate(){
    assertThatThrownBy(()->ProductStructurePolicy.requireAllowed(source("ENABLED",false,null),change(2,Set.of("spec"),null))).hasMessage("PRODUCT_STRUCTURE_REQUIRES_DISABLED");
    assertThatThrownBy(()->ProductStructurePolicy.requireAllowed(source("DISABLED",false,"UNKNOWN"),change(2,Set.of("spec"),"CONFIRMED_NONE"))).hasMessage("PRODUCT_LEGACY_SPEC_REQUIRED");
    assertThatCode(()->ProductStructurePolicy.requireAllowed(source("DISABLED",false,"CONFIRMED_NONE"),change(2,Set.of("spec"),"CONFIRMED_NONE"))).doesNotThrowAnyException();
  }
}
