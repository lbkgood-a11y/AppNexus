package com.appnexus.masterdata.adapter.web.organization;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 组织类型专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class OrganizationTypeDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("organization.type", "", "组织类型", "Organization types", List.of())), Map.of());
        return new Descriptor("OrganizationType", "组织类型", "Organization Type", "organization", 2, Category.MASTER_DATA, List.of(), List.of(
                operation("OrganizationType", "list", "组织类型：查看列表", "Read organization units", "organization.unit:read",
                        new PageOperation("organization/index", "/organization/manage", "组织类型：查看列表", "Read organization units", "GET", "/api/organization/domain/types")),
                operation("OrganizationType", "create", "组织类型：新增", "Create organization units", "organization.unit:create",
                        new PageOperation("organization/index", "/organization/manage", "组织类型：新增", "Create organization units", "POST", "/api/organization/domain/types")),
                operation("OrganizationType", "update", "组织类型：修改", "Update organization units", "organization.unit:update",
                        new PageOperation("organization/index", "/organization/manage", "组织类型：修改", "Update organization units", "PUT", "/api/organization/domain/types/{id}"))), access);
    }
}
