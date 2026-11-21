package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 审计事件专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class AuditEventDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var target = new DataField("target", "审计目标", "Audit target", "IDENTIFIER", Set.of("HIDDEN"));
        var tenant = new DataAccess("TENANT", List.of(
                new DataResource("audit.tenant", "", "租户审计记录", "Tenant audit records", List.of(target))), Map.of());
        var access = new DataAccess("PLATFORM", List.of(
                new DataResource("audit.platform", "", "平台审计记录", "Platform audit records", List.of(target))), Map.of(), List.of(), tenant);
        return new Descriptor("AuditEvent", "审计事件", "Audit Event", "audit", 3, Category.LEDGER, List.of(), List.of(
                operation("AuditEvent", "list", "审计事件：查看列表", "Grant organization permissions", "organization.permission:grant",
                        new PageOperation("account/profile", "/account/profile", "审计事件：查看列表", "Grant organization permissions", "GET", "/api/audits")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("AuditEvent", "platformList", "读取平台审计", "Read platform audit", "audit.entry:read")), access);
    }
}
