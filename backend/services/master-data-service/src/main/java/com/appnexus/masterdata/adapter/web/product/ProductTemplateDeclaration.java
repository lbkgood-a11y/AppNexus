package com.appnexus.masterdata.adapter.web.product;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 属性模板专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class ProductTemplateDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("masterdata.producttemplate", "", "属性模板", "Product templates", List.of()),
                new DataResource("masterdata.producttemplate.version", "masterdata.producttemplate", "模板版本", "Template versions", List.of()),
                new DataResource("masterdata.producttemplate.item", "masterdata.producttemplate", "模板项", "Template items", List.of())), Map.of());
        return new Descriptor("ProductTemplate", "属性模板", "Product templates", "master-data", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("ProductTemplate", "list", "查询属性模板", "List product templates", "masterdata.producttemplate:read",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "查询属性模板", "List product templates", "GET", "/api/master-data/product-template")),
                operation("ProductTemplate", "create", "新增属性模板", "Create product template", "masterdata.producttemplate:create",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "新增属性模板", "Create product template", "POST", "/api/master-data/product-template")),
                operation("ProductTemplate", "createVersion", "新增模板版本", "Create template version", "masterdata.producttemplate:update",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "新增模板版本", "Create template version", "POST", "/api/master-data/product-template/{id}/versions")),
                operation("ProductTemplate", "publish", "发布属性模板", "Publish product template", "masterdata.producttemplate:publish",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "发布属性模板", "Publish product template", "POST", "/api/master-data/product-template/versions/{id}/publish")),
                operation("ProductTemplate", "readVersions", "查看模板版本", "Read template versions", "masterdata.producttemplate:read",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "查看模板版本", "Read template versions", "GET", "/api/master-data/product-template/{id}/versions")),
                operation("ProductTemplate", "readItems", "查看模板项", "Read template items", "masterdata.producttemplate:read",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "查看模板项", "Read template items", "GET", "/api/master-data/product-template/versions/{id}/items")),
                operation("ProductTemplate", "update", "编辑模板定义", "Update template definition", "masterdata.producttemplate:update",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "编辑模板定义", "Update template definition", "PUT", "/api/master-data/product-template/{id}")),
                operation("ProductTemplate", "readStatusHistory", "查看启停历史", "Read status history", "masterdata.producttemplate:read",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "查看启停历史", "Read status history", "GET", "/api/master-data/product-template/{id}/status-history")),
                operation("ProductTemplate", "setStatus", "启停模板", "Set template status", "masterdata.producttemplate:set_status",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "启停模板", "Set template status", "PUT", "/api/master-data/product-template/{id}/status")),
                operation("ProductTemplate", "retire", "退役模板版本", "Retire template version", "masterdata.producttemplate:publish",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "退役模板版本", "Retire template version", "POST", "/api/master-data/product-template/versions/{id}/retire")),
                operation("ProductTemplate", "updateVersion", "编辑模板版本草稿", "Update template draft version", "masterdata.producttemplate:update",
                        new PageOperation("master-data/product-template", "/master-data/product-template", "编辑模板版本草稿", "Update template draft version", "PUT", "/api/master-data/product-template/versions/{id}"))), access);
    }
}
