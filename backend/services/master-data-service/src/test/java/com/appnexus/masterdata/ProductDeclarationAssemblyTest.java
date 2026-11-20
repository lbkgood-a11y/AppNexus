package com.appnexus.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.appnexus.masterdata.adapter.integration.product.ProductDeclaration;
import com.appnexus.masterdata.api.product.ProductManagement;
import com.appnexus.platform.authorization.DataAccessCatalog;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** 验证声明目录先于运行用例装配，避免商品授权依赖目录时形成 Bean 循环。 */
class ProductDeclarationAssemblyTest {
  /** 真实 Spring 构造器代理必须允许目录读取；运行用例仍可依赖同一完整目录。 */
  @Test
  void catalogCanInitializeBeforeProductUseCase() {
    var initialized = new AtomicInteger();
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(ProductDeclaration.class);
      context.registerBean(DataAccessCatalog.class,
          () -> DataAccessCatalog.fromObjects(List.of(context.getBean(ProductDeclaration.class))));
      context.registerBean(ProductManagement.class, () -> {
        assertThat(context.getBean(DataAccessCatalog.class)).isNotNull();
        initialized.incrementAndGet();
        return mock(ProductManagement.class);
      });
      context.refresh();
      assertThat(context.getBean(ProductDeclaration.class).runtime()).isNotNull();
      assertThat(initialized).hasValue(1);
    }
  }
}
