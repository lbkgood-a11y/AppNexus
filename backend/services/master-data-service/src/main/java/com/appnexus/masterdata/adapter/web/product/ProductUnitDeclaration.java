package com.appnexus.masterdata.adapter.web.product;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 计量单位专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class ProductUnitDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("masterdata.productunit", "", "计量单位", "Product units", List.of()),
                new DataResource("masterdata.productunit.group", "masterdata.productunit", "单位分组", "Unit groups", List.of()),
                new DataResource("masterdata.productunit.conversion", "masterdata.productunit", "单位换算", "Unit conversions", List.of())), Map.of());
        return new Descriptor("ProductUnit", "计量单位", "Product unit", "master-data", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("ProductUnit", "listGroups", "查询单位分组", "List unit groups", "masterdata.productunit:read",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "查询单位分组", "List unit groups", "GET", "/api/master-data/product-unit/groups")),
                operation("ProductUnit", "createGroup", "新增单位分组", "Create unit group", "masterdata.productunit:create",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "新增单位分组", "Create unit group", "POST", "/api/master-data/product-unit/groups")),
                operation("ProductUnit", "renameGroup", "编辑单位分组", "Rename unit group", "masterdata.productunit:update",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "编辑单位分组", "Rename unit group", "PUT", "/api/master-data/product-unit/groups/{code}")),
                operation("ProductUnit", "list", "查询计量单位", "List unit", "masterdata.productunit:read",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "查询计量单位", "List unit", "GET", "/api/master-data/product-unit")),
                operation("ProductUnit", "create", "新增计量单位", "Create unit", "masterdata.productunit:create",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "新增计量单位", "Create unit", "POST", "/api/master-data/product-unit")),
                operation("ProductUnit", "update", "编辑计量单位", "Update unit", "masterdata.productunit:update",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "编辑计量单位", "Update unit", "PUT", "/api/master-data/product-unit/{id}")),
                operation("ProductUnit", "readStatusHistory", "查看启停历史", "Read status history", "masterdata.productunit:read",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "查看启停历史", "Read status history", "GET", "/api/master-data/product-unit/{id}/status-history")),
                operation("ProductUnit", "readConversionStatusHistory", "查看启停历史", "Read status history", "masterdata.productunit:read",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "查看启停历史", "Read status history", "GET", "/api/master-data/product-unit/conversions/{id}/status-history")),
                operation("ProductUnit", "setStatus", "启停计量单位", "Set status", "masterdata.productunit:set_status",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "启停计量单位", "Set status", "PUT", "/api/master-data/product-unit/{id}/status")),
                operation("ProductUnit", "listConversions", "查询单位换算", "List conversions", "masterdata.productunit:read",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "查询单位换算", "List conversions", "GET", "/api/master-data/product-unit/conversions")),
                operation("ProductUnit", "createConversion", "新增单位换算", "Create conversion", "masterdata.productunit:convert",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "新增单位换算", "Create conversion", "POST", "/api/master-data/product-unit/conversions")),
                operation("ProductUnit", "setConversionStatus", "启停单位换算", "Set conversion status", "masterdata.productunit:convert",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "启停单位换算", "Set conversion status", "PUT", "/api/master-data/product-unit/conversions/{id}/status")),
                operation("ProductUnit", "calculateConversion", "计算单位换算", "Calculate conversion", "masterdata.productunit:read",
                        new PageOperation("master-data/product-unit", "/master-data/product-unit", "计算单位换算", "Calculate conversion", "GET", "/api/master-data/product-unit/conversions/{id}/calculate"))), access);
    }
}
