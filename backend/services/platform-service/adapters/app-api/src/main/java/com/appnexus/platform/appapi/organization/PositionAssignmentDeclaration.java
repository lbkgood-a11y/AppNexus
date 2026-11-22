package com.appnexus.platform.appapi.organization;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 人员任职专用入口的唯一对象声明；按关联组织继承授权中心配置的组织单元同操作范围。 */
@Component
public final class PositionAssignmentDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("organization.assignment", "", "人员任职", "Position assignments", List.of())), Map.of());
        return new Descriptor("PositionAssignment", "人员任职", "Position Assignment", "organization", 3, Category.MASTER_DATA, List.of(), List.of(
                operation("PositionAssignment", "list", "人员任职：查看列表", "Read organization units", "organization.unit:read",
                        new PageOperation("organization/index", "/organization/manage", "人员任职：查看列表", "Read organization units", "GET", "/api/organization/domain/assignments")),
                operation("PositionAssignment", "create", "人员任职：新增", "Create organization units", "organization.unit:create",
                        new PageOperation("organization/index", "/organization/manage", "人员任职：新增", "Create organization units", "POST", "/api/organization/domain/assignments")),
                operation("PositionAssignment", "update", "人员任职：修改", "Update organization units", "organization.unit:update",
                        new PageOperation("organization/index", "/organization/manage", "人员任职：修改", "Update organization units", "PUT", "/api/organization/domain/assignments/{id}"))), access);
    }
}
