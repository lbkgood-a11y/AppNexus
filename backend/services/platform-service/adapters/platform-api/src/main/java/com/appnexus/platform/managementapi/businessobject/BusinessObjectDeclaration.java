package com.appnexus.platform.managementapi.businessobject;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 业务对象配置专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class BusinessObjectDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var tenant = new DataAccess("TENANT", List.of(
                new DataResource("business.object.configuration", "", "租户对象配置", "Tenant object configuration", List.of())), Map.of());
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource("business.object.catalog", "", "业务对象目录", "Business object catalog", List.of())), Map.of(), List.of(), tenant);
        return new Descriptor("BusinessObject", "业务对象配置", "Business Object", "configuration", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("BusinessObject", "relationships", "业务对象配置：查看入口关联", "View page relationships", "business.object.catalog:manage",
                        new PageOperation("platform/business-objects", "/platform/business-objects", "业务对象配置：查看入口关联", "View page relationships", "GET", "/bff/platform/business-objects/{type}/page-relationships")),
                operation("BusinessObject", "control", "业务对象配置：管控入口", "Control page entry", "business.object.catalog:manage",
                        new PageOperation("platform/business-objects", "/platform/business-objects", "业务对象配置：管控入口", "Control page entry", "PUT", "/bff/platform/business-objects/{type}/page-controls")),
                operation("BusinessObject", "catalog", "业务对象配置：查看目录", "View document registry", "business.object.catalog:manage",
                        new PageOperation("platform/business-objects", "/platform/business-objects", "业务对象配置：查看目录", "View document registry", "GET", "/bff/platform/business-objects")),
                operation("BusinessObject", "state", "业务对象配置：启停类型", "Change document state", "business.object.catalog:manage",
                        new PageOperation("platform/business-objects", "/platform/business-objects", "业务对象配置：启停类型", "Change document state", "PUT", "/bff/platform/business-objects/{type}/state")),
                operation("BusinessObject", "grants", "业务对象配置：查看租户授权", "View document grants", "business.object.catalog:manage",
                        new PageOperation("platform/business-objects", "/platform/business-objects", "业务对象配置：查看租户授权", "View document grants", "GET", "/bff/platform/business-objects/{type}/grants")),
                operation("BusinessObject", "grant", "业务对象配置：授予操作权限", "Grant document access", "business.object.catalog:manage",
                        new PageOperation("platform/business-objects", "/platform/business-objects", "业务对象配置：授予操作权限", "Grant document access", "PUT", "/bff/platform/business-objects/{type}/grants/{tenant}")),
                operation("BusinessObject", "tenantCatalog", "业务对象配置：查看已开放对象", "View document workbench", "business.object.configuration:manage",
                        new PageOperation("business-objects/index", "/business-objects/workbench", "业务对象配置：查看已开放对象", "View document workbench", "GET", "/api/business-objects")),
                operation("BusinessObject", "readiness", "业务对象配置：检查配置就绪", "Check readiness", "business.object.configuration:manage",
                        new PageOperation("business-objects/index", "/business-objects/workbench", "业务对象配置：检查配置就绪", "Check readiness", "GET", "/api/business-objects/{type}/readiness")),
                operation("BusinessObject", "configuration", "业务对象配置：查看配置", "View configuration", "business.object.configuration:manage",
                        new PageOperation("business-objects/index", "/business-objects/workbench", "业务对象配置：查看配置", "View configuration", "GET", "/api/business-objects/{type}/configuration")),
                operation("BusinessObject", "save", "业务对象配置：保存", "Save document configuration", "business.object.configuration:manage",
                        new PageOperation("business-objects/index", "/business-objects/workbench", "业务对象配置：保存", "Save document configuration", "PUT", "/api/business-objects/{type}/configuration")),
                operation("BusinessObject", "publish", "业务对象配置：发布", "Publish document configuration", "business.object.configuration:manage",
                        new PageOperation("business-objects/index", "/business-objects/workbench", "业务对象配置：发布", "Publish document configuration", "POST", "/api/business-objects/{type}/configuration:publish")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessObject", "executePlatformCommand", "分发平台对象命令", "Dispatch platform object command", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessObject", "inspectOperation", "诊断对象操作", "Inspect object operation", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessObject", "resolveIntegration", "集成对象解析", "Resolve integration object", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessObject", "contracts", "读取交互契约", "Read interaction contracts", "business.object.catalog:manage"),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessObject", "printScenes", "查询对象打印场景", "Read object print scenes", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("BusinessObject", "workflowOptions", "查询对象流程候选", "Read object workflow options", "")), access);
    }
}
