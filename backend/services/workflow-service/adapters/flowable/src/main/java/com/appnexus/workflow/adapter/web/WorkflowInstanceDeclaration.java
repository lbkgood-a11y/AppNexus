package com.appnexus.workflow.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 流程实例专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public final class WorkflowInstanceDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("workflow.instance", "", "流程实例", "Workflow instances", List.of()),
                new DataResource("workflow.instance.actor", "workflow.instance", "流程参与人", "Workflow participants", List.of())), Map.of());
        return new Descriptor("WorkflowInstance", "流程实例", "Workflow Instance", "workflow", 2, Category.TRANSACTION, List.of(), List.of(
                operation("WorkflowInstance", "start", "流程实例：发起", "Start workflow", "workflow:start",
                        new PageOperation("workflow/index", "/workflow/center", "流程实例：发起", "Start workflow", "POST", "/api/workflow/instances")),
                operation("WorkflowInstance", "withdraw", "流程实例：撤回", "Withdraw instance", "",
                        new PageOperation("workflow/index", "/workflow/center", "流程实例：撤回", "Withdraw instance", "POST", "/api/workflow/instances/{id}/withdraw")),
                operation("WorkflowInstance", "read", "流程实例：查看", "View instance", "",
                        new PageOperation("workflow/index", "/workflow/center", "流程实例：查看", "View instance", "GET", "/api/workflow/instances/{id}")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("WorkflowInstance", "list", "查询流程实例", "List workflow instances", "")), access);
    }
}
