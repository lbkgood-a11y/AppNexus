package com.appnexus.masterdata.adapter.web.product;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 商品属性专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class ProductAttributeDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("masterdata.productattribute", "", "商品属性", "Product attributes", List.of()),
                new DataResource("masterdata.productattribute.group", "masterdata.productattribute", "属性组", "Attribute groups", List.of()),
                new DataResource("masterdata.productattribute.revision", "masterdata.productattribute", "属性版本", "Attribute revisions", List.of()),
                new DataResource("masterdata.productattribute.option", "masterdata.productattribute", "属性选项", "Attribute options", List.of())), Map.of());
        return new Descriptor("ProductAttribute", "商品属性", "Product attributes", "master-data", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("ProductAttribute", "listGroups", "查询属性分组", "List attribute groups", "masterdata.productattribute:read",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "查询属性分组", "List attribute groups", "GET", "/api/master-data/product-attribute/groups")),
                operation("ProductAttribute", "createGroup", "新增属性分组", "Create attribute group", "masterdata.productattribute:create",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "新增属性分组", "Create attribute group", "POST", "/api/master-data/product-attribute/groups")),
                operation("ProductAttribute", "list", "查询商品属性", "List product attributes", "masterdata.productattribute:read",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "查询商品属性", "List product attributes", "GET", "/api/master-data/product-attribute")),
                operation("ProductAttribute", "create", "新增商品属性及初始草稿", "Create product attribute with initial draft", "masterdata.productattribute:create",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "新增商品属性及初始草稿", "Create product attribute with initial draft", "POST", "/api/master-data/product-attribute")),
                operation("ProductAttribute", "createRevision", "新增属性修订", "Create attribute revision", "masterdata.productattribute:update",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "新增属性修订", "Create attribute revision", "POST", "/api/master-data/product-attribute/{id}/revisions")),
                operation("ProductAttribute", "publishRevision", "发布属性修订", "Publish attribute revision", "masterdata.productattribute:publish",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "发布属性修订", "Publish attribute revision", "POST", "/api/master-data/product-attribute/revisions/{id}/publish")),
                operation("ProductAttribute", "createOption", "新增属性选项", "Create attribute option", "masterdata.productattribute:update",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "新增属性选项", "Create attribute option", "POST", "/api/master-data/product-attribute/revisions/{id}/options")),
                operation("ProductAttribute", "readRevisions", "查看属性修订", "Read attribute revisions", "masterdata.productattribute:read",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "查看属性修订", "Read attribute revisions", "GET", "/api/master-data/product-attribute/{id}/revisions")),
                operation("ProductAttribute", "readOptions", "查看属性选项", "Read attribute options", "masterdata.productattribute:read",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "查看属性选项", "Read attribute options", "GET", "/api/master-data/product-attribute/revisions/{id}/options")),
                operation("ProductAttribute", "updateGroup", "维护商品属性配置", "Maintain product attribute configuration", "masterdata.productattribute:update",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "维护商品属性配置", "Maintain product attribute configuration", "PUT", "/api/master-data/product-attribute/groups/{id}")),
                operation("ProductAttribute", "update", "维护商品属性配置", "Maintain product attribute configuration", "masterdata.productattribute:update",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "维护商品属性配置", "Maintain product attribute configuration", "PUT", "/api/master-data/product-attribute/{id}")),
                operation("ProductAttribute", "setGroupStatus", "维护商品属性配置", "Maintain product attribute configuration", "masterdata.productattribute:set_status",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "维护商品属性配置", "Maintain product attribute configuration", "PUT", "/api/master-data/product-attribute/groups/{id}/status")),
                operation("ProductAttribute", "readGroupStatusHistory", "查看启停历史", "Read status history", "masterdata.productattribute:read",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "查看启停历史", "Read status history", "GET", "/api/master-data/product-attribute/groups/{id}/status-history")),
                operation("ProductAttribute", "readStatusHistory", "查看启停历史", "Read status history", "masterdata.productattribute:read",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "查看启停历史", "Read status history", "GET", "/api/master-data/product-attribute/{id}/status-history")),
                operation("ProductAttribute", "setStatus", "维护商品属性配置", "Maintain product attribute configuration", "masterdata.productattribute:set_status",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "维护商品属性配置", "Maintain product attribute configuration", "PUT", "/api/master-data/product-attribute/{id}/status")),
                operation("ProductAttribute", "retireRevision", "退役属性修订", "Retire attribute revision", "masterdata.productattribute:publish",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "退役属性修订", "Retire attribute revision", "POST", "/api/master-data/product-attribute/revisions/{id}/retire")),
                operation("ProductAttribute", "updateRevision", "编辑属性修订草稿", "Update attribute revision draft", "masterdata.productattribute:update",
                        new PageOperation("master-data/product-attribute", "/master-data/product-attribute", "编辑属性修订草稿", "Update attribute revision draft", "PUT", "/api/master-data/product-attribute/revisions/{id}"))), access);
    }
}
