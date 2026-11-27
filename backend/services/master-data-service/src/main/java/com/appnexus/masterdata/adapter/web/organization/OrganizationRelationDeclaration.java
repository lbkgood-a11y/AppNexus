package com.appnexus.masterdata.adapter.web.organization;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 组织关系专用入口的唯一对象声明；按关联组织继承授权中心配置的组织单元同操作范围。 */
@Component
public final class OrganizationRelationDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("organization.relation", "", "组织关系", "Organization relations", List.of())), Map.of());
        return new Descriptor("OrganizationRelation", "组织关系", "Organization Relation", "organization", 3, Category.MASTER_DATA, List.of(), List.of(
                operation("OrganizationRelation", "list", "组织关系：查看列表", "Read organization units", "organization.unit:read",
                        new PageOperation("organization/index", "/organization/manage", "组织关系：查看列表", "Read organization units", "GET", "/api/organization/domain/relations")),
                operation("OrganizationRelation", "create", "组织关系：新增", "Create organization units", "organization.unit:create",
                        new PageOperation("organization/index", "/organization/manage", "组织关系：新增", "Create organization units", "POST", "/api/organization/domain/relations")),
                operation("OrganizationRelation", "delete", "组织关系：删除", "Delete organization units", "organization.unit:delete",
                        new PageOperation("organization/index", "/organization/manage", "组织关系：删除", "Delete organization units", "DELETE", "/api/organization/domain/relations/{id}"))), access);
    }
}
