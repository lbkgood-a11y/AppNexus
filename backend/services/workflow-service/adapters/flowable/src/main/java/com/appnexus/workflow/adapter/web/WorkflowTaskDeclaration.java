package com.appnexus.workflow.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 流程任务专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public final class WorkflowTaskDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("workflow.task", "", "流程任务", "Workflow tasks", List.of()),
                new DataResource("workflow.task.assignment", "workflow.task", "任务指派", "Task assignments", List.of())), Map.of());
        return new Descriptor("WorkflowTask", "流程任务", "Workflow Task", "workflow", 2, Category.TRANSACTION, List.of(), List.of(
                operation("WorkflowTask", "list", "流程任务：查看列表", "View tasks", "",
                        new PageOperation("workflow/index", "/workflow/center", "流程任务：查看列表", "View tasks", "GET", "/api/workflow/tasks")),
                operation("WorkflowTask", "read", "流程任务：查看", "View assigned task", "",
                        new PageOperation("workflow/index", "/workflow/center", "流程任务：查看", "View assigned task", "GET", "/api/workflow/tasks/{id}")),
                operation("WorkflowTask", "complete", "流程任务：处理", "Act on task", "",
                        new PageOperation("workflow/index", "/workflow/center", "流程任务：处理", "Act on task", "POST", "/api/workflow/tasks/{id}/actions")),
                operation("WorkflowTask", "reassign", "流程任务：转派", "Reassign task", "workflow:operate",
                        new PageOperation("workflow/index", "/workflow/center", "流程任务：转派", "Reassign task", "POST", "/api/workflow/tasks/{id}/reassign"))), access);
    }
}
