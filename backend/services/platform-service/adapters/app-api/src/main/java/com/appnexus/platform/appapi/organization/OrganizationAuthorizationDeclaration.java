package com.appnexus.platform.appapi.organization;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 组织授权专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class OrganizationAuthorizationDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("organization.authorization", "", "组织授权", "Organization authorizations", List.of())), Map.of());
        return new Descriptor("OrganizationAuthorization", "组织授权", "Organization authorization", "authorization", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("OrganizationAuthorization", "memberOptions", "组织授权：查看成员选项", "Grant organization permissions", "organization.permission:grant",
                        new PageOperation("organization/index", "/organization/manage", "组织授权：查看成员选项", "Grant organization permissions", "GET", "/api/organization/grants/members")),
                operation("OrganizationAuthorization", "list", "组织授权：查看列表", "Grant organization permissions", "organization.permission:grant",
                        new PageOperation("organization/index", "/organization/manage", "组织授权：查看列表", "Grant organization permissions", "GET", "/api/organization/grants/{memberId}")),
                operation("OrganizationAuthorization", "grant", "组织授权：授予操作权限", "Grant organization permissions", "organization.permission:grant",
                        new PageOperation("organization/index", "/organization/manage", "组织授权：授予操作权限", "Grant organization permissions", "POST", "/api/organization/grants")),
                operation("OrganizationAuthorization", "revoke", "组织授权：撤销", "Grant organization permissions", "organization.permission:grant",
                        new PageOperation("organization/index", "/organization/manage", "组织授权：撤销", "Grant organization permissions", "DELETE", "/api/organization/grants/{memberId}/{capability}"))), access);
    }
}
