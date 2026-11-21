package com.appnexus.masterdata.adapter.web.masterdata;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 仓库管理专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class WarehouseDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("masterdata.warehouse", "", "仓库档案", "Warehouses", List.of(
                        field("contactName", "联系人", "Contact name", "PERSON_NAME"),
                        field("phone", "联系电话", "Telephone", "PHONE"),
                        field("email", "联系邮箱", "Contact email", "EMAIL"),
                        field("ownershipCertificate", "权属证明", "Ownership certificate", "IDENTIFIER"),
                        field("personalGuarantee", "个人担保", "Personal guarantee", "TEXT")))), Map.of());
        return new Descriptor("Warehouse", "仓库管理", "Warehouses", "master-data", 4, Category.MASTER_DATA, List.of(), List.of(
                operation("Warehouse", "fieldRestrictions", "读取仓库字段限制", "Read warehouse field restrictions", "masterdata.warehouse:read",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "读取仓库字段限制", "Read warehouse field restrictions", "GET", "/api/master-data/warehouse/field-restrictions")),
                operation("Warehouse", "list", "查询仓库管理", "list Warehouses", "masterdata.warehouse:read",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "查询仓库管理", "list Warehouses", "GET", "/api/master-data/warehouse")),
                operation("Warehouse", "create", "新增仓库管理", "create Warehouses", "masterdata.warehouse:create",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "新增仓库管理", "create Warehouses", "POST", "/api/master-data/warehouse")),
                operation("Warehouse", "update", "编辑仓库管理", "update Warehouses", "masterdata.warehouse:update",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "编辑仓库管理", "update Warehouses", "PUT", "/api/master-data/warehouse/{id}")),
                operation("Warehouse", "setStatus", "启停仓库管理", "setStatus Warehouses", "masterdata.warehouse:set_status",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "启停仓库管理", "setStatus Warehouses", "PUT", "/api/master-data/warehouse/{id}/status")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Warehouse", "mapConfiguration", "地图配置", "Map configuration", "masterdata.warehouse:read"),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Warehouse", "mapProxy", "仓库地图资源", "Warehouse map resource", "masterdata.warehouse:read")), access);
    }

    private DataField field(String code, String nameZh, String nameEn, String kind) {
        return new DataField(code, nameZh, nameEn, kind, Set.of("READ_ONLY", "MASKED", "HIDDEN"));
    }
}
