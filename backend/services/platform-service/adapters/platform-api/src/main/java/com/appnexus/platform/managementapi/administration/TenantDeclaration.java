package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.InteractionContract;
import com.appnexus.platform.tenant.PlatformAdministration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 平台租户档案的唯一声明；租户开通与字典、地理数据初始化仍由原事务执行。 */
@Component
public final class TenantDeclaration implements Provider {
    private static final String PAGE = "platform/tenants";
    private static final String API = "/bff/platform/tenants";

    /** @return 平台租户档案与四个已有专用操作的契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("PLATFORM", List.of(new DataResource(
                PlatformAdministration.TENANT_RESOURCE, "", "租户档案", "Tenants", List.of())), Map.of());
        return new Descriptor("Tenant", "租户", "Tenant", "tenant", 1, Category.SYSTEM, List.of(), List.of(
                operation("list", "查看租户", "Read tenants", "tenant:read", "GET", API),
                operation("create", "新增租户", "Create tenant", "tenant:create", "POST", API),
                operation("update", "修改租户", "Update tenant", "tenant:update", "PUT", API + "/{id}"),
                operation("setEnabled", "启停租户", "Enable or disable tenant", "tenant:enable", "PUT", API + "/{id}/state")), access);
    }

    private Operation operation(String code, String nameZh, String nameEn, String permission, String method, String api) {
        String id = "Tenant." + code;
        var page = new PageOperation(PAGE, "/" + PAGE, nameZh, nameEn, method, api);
        var interaction = new InteractionContract(id, 1, "Tenant", code, nameZh, nameEn,
                InteractionContract.Trigger.USER_ACTION, List.of());
        return new Operation(code, nameZh, nameEn, permission, id, page, List.of(), interaction);
    }
}
