package com.appnexus.platform.appapi.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 租户参数专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class TenantParameterDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("configuration.parameter", "", "租户参数", "Tenant parameters", List.of())), Map.of());
        return new Descriptor("TenantParameter", "租户参数", "Tenant Parameter", "configuration", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("TenantParameter", "save", "租户参数：保存", "Save tenant parameter", "configuration.parameter:save",
                        new PageOperation("configuration/parameters", "/configuration/parameters", "租户参数：保存", "Save tenant parameter", "PUT", "/api/configuration/parameters/{key}")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("TenantParameter", "list", "读取租户参数", "Read tenant parameters", "")), access);
    }
}
