package com.appnexus.masterdata.adapter.web.product;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 商品分类专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class ProductCategoryDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("masterdata.productcategory", "", "商品分类", "Product categories", List.of()),
                new DataResource("masterdata.productcategory.configuration", "masterdata.productcategory", "分类配置版本", "Category configurations", List.of())), Map.of());
        return new Descriptor("ProductCategory", "商品分类", "Product category", "master-data", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("ProductCategory", "list", "查询商品分类", "List category", "masterdata.productcategory:read",
                        new PageOperation("master-data/product-category", "/master-data/product-category", "查询商品分类", "List category", "GET", "/api/master-data/product-category"),
                        new PageOperation("master-data/product", "/master-data/product", "查询商品分类", "List category", "GET", "/api/master-data/product-category"),
                        new PageOperation("master-data/product-sku", "/master-data/product-sku", "查询商品分类", "List category", "GET", "/api/master-data/product-category")),
                operation("ProductCategory", "create", "新增商品分类", "Create category", "masterdata.productcategory:create",
                        new PageOperation("master-data/product-category", "/master-data/product-category", "新增商品分类", "Create category", "POST", "/api/master-data/product-category")),
                operation("ProductCategory", "update", "编辑商品分类", "Update category", "masterdata.productcategory:update",
                        new PageOperation("master-data/product-category", "/master-data/product-category", "编辑商品分类", "Update category", "PUT", "/api/master-data/product-category/{id}")),
                operation("ProductCategory", "setStatus", "启停商品分类", "Set status", "masterdata.productcategory:set_status",
                        new PageOperation("master-data/product-category", "/master-data/product-category", "启停商品分类", "Set status", "PUT", "/api/master-data/product-category/{id}/status")),
                operation("ProductCategory", "publishConfiguration", "发布分类配置", "Publish configuration", "masterdata.productcategory:publish",
                        new PageOperation("master-data/product-category", "/master-data/product-category", "发布分类配置", "Publish configuration", "POST", "/api/master-data/product-category/{id}/configurations")),
                operation("ProductCategory", "readConfiguration", "查看分类配置", "Read configuration", "masterdata.productcategory:read",
                        new PageOperation("master-data/product-category", "/master-data/product-category", "查看分类配置", "Read configuration", "GET", "/api/master-data/product-category/{id}/configurations/{configId}"))), access);
    }
}
