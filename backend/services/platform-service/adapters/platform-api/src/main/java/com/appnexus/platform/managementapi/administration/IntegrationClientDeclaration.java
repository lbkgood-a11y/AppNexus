package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.InteractionContract;
import com.appnexus.platform.security.IntegrationClients;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 平台集成客户端的唯一声明；机器身份的令牌能力仍由 OAuth 安全链执行。 */
@Component
public final class IntegrationClientDeclaration implements Provider {
    private static final String TENANT_CLIENTS = "/bff/platform/tenants/{tenant}/clients";
    private static final String CLIENT = "/bff/platform/clients/{id}";

    /** @return 专用管理操作及平台资源；密钥和令牌不属于通用数据投影 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("PLATFORM", List.of(new DataResource(
                IntegrationClients.RESOURCE, "", "集成客户端", "Integration clients", List.of())), Map.of());
        return new Descriptor("IntegrationClient", "集成客户端", "Integration Client", "identity", 1,
                Category.SYSTEM, List.of(), List.of(
                        operation("list", "查看集成客户端", "Read integration clients",
                                "integration.client:read", "GET", TENANT_CLIENTS),
                        operation("create", "新增集成客户端", "Create integration clients",
                                "integration.client:create", "POST", TENANT_CLIENTS),
                        operation("update", "修改集成客户端", "Update integration clients",
                                "integration.client:update", "PUT", CLIENT),
                        operation("rotate", "轮换客户端密钥", "Rotate integration secrets",
                                "integration.client:rotate_secret", "POST", CLIENT + "/secret-rotation")), access);
    }

    private Operation operation(String code, String nameZh, String nameEn, String permission, String method, String api) {
        String id = "IntegrationClient." + code;
        var page = new PageOperation("platform/tenants", "/platform/tenants", nameZh, nameEn, method, api);
        var interaction = new InteractionContract(id, 1, "IntegrationClient", code, nameZh, nameEn,
                InteractionContract.Trigger.USER_ACTION, List.of());
        return new Operation(code, nameZh, nameEn, permission, id, page, List.of(), interaction);
    }
}
