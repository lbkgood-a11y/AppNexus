package com.appnexus.platform.appapi.organization;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 岗位专用入口的唯一对象声明；按关联组织继承授权中心配置的组织单元同操作范围。 */
@Component
public final class PositionDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("organization.position", "", "岗位", "Positions", List.of())), Map.of());
        return new Descriptor("Position", "岗位", "Position", "organization", 3, Category.MASTER_DATA, List.of(), List.of(
                operation("Position", "list", "岗位：查看列表", "Read organization units", "organization.unit:read",
                        new PageOperation("organization/index", "/organization/manage", "岗位：查看列表", "Read organization units", "GET", "/api/organization/domain/positions")),
                operation("Position", "create", "岗位：新增", "Create organization units", "organization.unit:create",
                        new PageOperation("organization/index", "/organization/manage", "岗位：新增", "Create organization units", "POST", "/api/organization/domain/positions")),
                operation("Position", "update", "岗位：修改", "Update organization units", "organization.unit:update",
                        new PageOperation("organization/index", "/organization/manage", "岗位：修改", "Update organization units", "PUT", "/api/organization/domain/positions/{id}"))), access);
    }
}
