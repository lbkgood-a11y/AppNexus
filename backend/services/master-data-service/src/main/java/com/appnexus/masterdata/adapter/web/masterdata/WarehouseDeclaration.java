package com.appnexus.masterdata.adapter.web.masterdata;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 仓库管理专用入口的唯一对象声明；标准 CRUD 使用平台门禁，字典范围和字段保护沿用现有策略。 */
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
                        field("personalGuarantee", "个人担保", "Personal guarantee", "TEXT")))), Map.of("list",Set.of("ALL","RULES"),"create",Set.of("ALL","RULES"),
                "selectLogin",Set.of("RULES"),"update",Set.of("ALL","RULES"),"setStatus",Set.of("ALL","RULES")),
                List.of(new DataDimension("organizationId","归属组织","Owning organization","UUID",Set.of("EQ","IN"),"ORGANIZATION","",
                        Set.of("list","create","update","setStatus"),Set.of("CURRENT_ORGANIZATION","CURRENT_ORGANIZATION_SUBTREE")),
                        new DataDimension("warehouseType","仓库类型","Warehouse type","TEXT",Set.of("EQ","IN"),"DICTIONARY",
                        com.appnexus.platform.configuration.BusinessValueCatalog.WAREHOUSE.get("warehouseType"),Set.of("list")),
                        new DataDimension("operatingMode","经营模式","Operating mode","TEXT",Set.of("EQ","IN"),"DICTIONARY",
                        com.appnexus.platform.configuration.BusinessValueCatalog.WAREHOUSE.get("operatingMode"),Set.of("list"))))
                .withStandardCrud("organizationId",Set.of("list","create","update","setStatus"));
        return new Descriptor("Warehouse", "仓库管理", "Warehouses", "master-data", 9, Category.MASTER_DATA, List.of(), List.of(
                operation("Warehouse", "fieldRestrictions", "读取仓库字段限制", "Read warehouse field restrictions", "masterdata.warehouse:read",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "读取仓库字段限制", "Read warehouse field restrictions", "GET", "/api/master-data/warehouse/field-restrictions")),
                operation("Warehouse", "list", "查询仓库管理", "list Warehouses", "masterdata.warehouse:read",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "查询仓库管理", "list Warehouses", "GET", "/api/master-data/warehouse")),
                operation("Warehouse", "selectLogin", "登录选仓", "Select login warehouse", "masterdata.warehouse:select_login",
                        new PageOperation("warehousing/inventory-overview", "/warehousing/inventory-overview", "读取登录选仓", "Read login warehouse selection", "GET", "/api/master-data/warehouse/login-selection"),
                        new PageOperation("warehousing/inventory-overview", "/warehousing/inventory-overview", "设置登录仓库", "Set login warehouse", "PUT", "/api/master-data/warehouse/login-selection")),
                operation("Warehouse", "create", "新增仓库管理", "create Warehouses", "masterdata.warehouse:create",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "新增仓库管理", "create Warehouses", "POST", "/api/master-data/warehouse")),
                operation("Warehouse", "update", "编辑仓库管理", "update Warehouses", "masterdata.warehouse:update",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "编辑仓库管理", "update Warehouses", "PUT", "/api/master-data/warehouse/{id}")),
                operation("Warehouse", "readStatusHistory", "查看启停历史", "Read status history", "masterdata.warehouse:read",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "查看启停历史", "Read status history", "GET", "/api/master-data/warehouse/{id}/status-history")),
                operation("Warehouse", "setStatus", "启停仓库管理", "setStatus Warehouses", "masterdata.warehouse:set_status",
                        new PageOperation("master-data/warehouse", "/master-data/warehouse", "启停仓库管理", "setStatus Warehouses", "PUT", "/api/master-data/warehouse/{id}/status")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Warehouse", "mapConfiguration", "地图配置", "Map configuration", "masterdata.warehouse:read"),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("Warehouse", "mapProxy", "仓库地图资源", "Warehouse map resource", "masterdata.warehouse:read")), access);
    }

    private DataField field(String code, String nameZh, String nameEn, String kind) {
        return new DataField(code, nameZh, nameEn, kind, Set.of("READ_ONLY", "MASKED", "HIDDEN"));
    }
}
