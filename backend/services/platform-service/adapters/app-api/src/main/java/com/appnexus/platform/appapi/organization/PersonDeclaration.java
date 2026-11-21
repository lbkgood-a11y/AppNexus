package com.appnexus.platform.appapi.organization;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 人员管理专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
public final class PersonDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("masterdata.person", "", "人员档案", "Personnel", List.of(
                        field("phone","联系电话","Phone","PHONE"),field("email","邮箱","Email","EMAIL"),
                        field("identityNumber","证件号码","Identity number","IDENTIFIER"),
                        field("emergencyContact","紧急联系人","Emergency contact","PERSON_NAME"),
                        field("emergencyPhone","紧急联系电话","Emergency phone","PHONE"))),
                new DataResource("masterdata.person.detail", "masterdata.person", "人员明细", "Personnel details", List.of(
                        field("contactValue","联系方式","Contact value","TEXT"),field("address","详细地址","Address","TEXT"),
                        field("certificateNumber","证书编号","Certificate number","IDENTIFIER")))), Map.of());
        return new Descriptor("Person", "人员管理", "Personnel", "organization", 3, Category.MASTER_DATA, List.of(), List.of(
                operation("Person", "fieldRestrictions", "读取人员字段限制", "Read personnel field restrictions", "masterdata.person:read",
                        new PageOperation("organization/personnel", "/organization/personnel", "读取人员字段限制", "Read personnel field restrictions", "GET", "/api/master-data/person/field-restrictions")),
                operation("Person", "list", "查询人员管理", "list Personnel", "masterdata.person:read",
                        new PageOperation("organization/personnel", "/organization/personnel", "查询人员管理", "list Personnel", "GET", "/api/master-data/person")),
                operation("Person", "create", "新增人员管理", "create Personnel", "masterdata.person:create",
                        new PageOperation("organization/personnel", "/organization/personnel", "新增人员管理", "create Personnel", "POST", "/api/master-data/person")),
                operation("Person", "update", "编辑人员管理", "update Personnel", "masterdata.person:update",
                        new PageOperation("organization/personnel", "/organization/personnel", "编辑人员管理", "update Personnel", "PUT", "/api/master-data/person/{id}")),
                operation("Person", "setStatus", "启停人员管理", "setStatus Personnel", "masterdata.person:set_status",
                        new PageOperation("organization/personnel", "/organization/personnel", "启停人员管理", "setStatus Personnel", "PUT", "/api/master-data/person/{id}/status")),
                operation("Person", "read", "读取人员详情", "Read personnel profile", "masterdata.person:read",
                        new PageOperation("organization/personnel", "/organization/personnel", "读取人员详情", "Read personnel profile", "GET", "/api/master-data/person/{id}")),
                operation("Person", "options", "读取人员关联目录", "Read personnel directory", "masterdata.person:read",
                        new PageOperation("organization/personnel", "/organization/personnel", "读取人员关联目录", "Read personnel directory", "GET", "/api/master-data/person/options")),
                operation("Person", "createDetail", "新增人员明细", "Create personnel detail", "masterdata.person:detail_create",
                        new PageOperation("organization/personnel", "/organization/personnel", "新增人员明细", "Create personnel detail", "POST", "/api/master-data/person/{id}/details/{section}")),
                operation("Person", "updateDetail", "编辑人员明细", "Edit personnel detail", "masterdata.person:detail_update",
                        new PageOperation("organization/personnel", "/organization/personnel", "编辑人员明细", "Edit personnel detail", "PUT", "/api/master-data/person/{id}/details/{section}/{detailId}")),
                operation("Person", "deleteDetail", "删除人员明细", "Delete personnel detail", "masterdata.person:detail_delete",
                        new PageOperation("organization/personnel", "/organization/personnel", "删除人员明细", "Delete personnel detail", "DELETE", "/api/master-data/person/{id}/details/{section}/{detailId}")),
                operation("Person", "assign", "分配人员用户", "Assign personnel user", "masterdata.person:assign",
                        new PageOperation("organization/personnel", "/organization/personnel", "分配人员用户", "Assign personnel user", "POST", "/api/master-data/person/{id}/users")),
                operation("Person", "updateAssignment", "编辑人员用户关联", "Edit personnel user assignment", "masterdata.person:assign",
                        new PageOperation("organization/personnel", "/organization/personnel", "编辑人员用户关联", "Edit personnel user assignment", "PUT", "/api/master-data/person/{id}/users/{assignmentId}")),
                operation("Person", "unassign", "解除人员用户关联", "Remove personnel user assignment", "masterdata.person:assign",
                        new PageOperation("organization/personnel", "/organization/personnel", "解除人员用户关联", "Remove personnel user assignment", "DELETE", "/api/master-data/person/{id}/users/{assignmentId}")),
                operation("Person", "myIdentities", "查看本人人员身份", "Read own personnel identities", "",
                        new PageOperation("organization/personnel", "/organization/personnel", "查看本人人员身份", "Read own personnel identities", "GET", "/api/master-data/person/my-identities")),
                operation("Person", "currentIdentity", "查看当前人员身份", "Read current personnel identity", "",
                        new PageOperation("organization/personnel", "/organization/personnel", "查看当前人员身份", "Read current personnel identity", "GET", "/api/master-data/person/identity")),
                operation("Person", "selectIdentity", "选择本次人员身份", "Select personnel identity", "",
                        new PageOperation("organization/personnel", "/organization/personnel", "选择本次人员身份", "Select personnel identity", "POST", "/api/master-data/person/identity"))), access);
    }

    private DataField field(String field,String zh,String en,String kind) {
        return new DataField(field,zh,en,kind,Set.of("READ_ONLY","MASKED","HIDDEN"));
    }
}
