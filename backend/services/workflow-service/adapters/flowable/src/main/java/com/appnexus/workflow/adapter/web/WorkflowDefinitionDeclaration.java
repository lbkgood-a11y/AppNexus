package com.appnexus.workflow.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 流程定义专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public final class WorkflowDefinitionDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("workflow.definition", "", "流程定义", "Workflow definitions", List.of()),
                new DataResource("workflow.definition.version", "workflow.definition", "流程定义版本", "Workflow definition versions", List.of())), Map.of());
        return new Descriptor("WorkflowDefinition", "流程定义", "Workflow Definition", "workflow", 2, Category.CONFIGURATION, List.of(), List.of(
                operation("WorkflowDefinition", "preview", "流程定义：预演", "Preview workflow", "workflow:edit",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：预演", "Preview workflow", "POST", "/api/workflow/preview")),
                operation("WorkflowDefinition", "list", "流程定义：查看列表", "View definitions", "workflow:read",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：查看列表", "View definitions", "GET", "/api/workflow/definitions")),
                operation("WorkflowDefinition", "validate", "流程定义：校验", "Validate workflow", "workflow:edit",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：校验", "Validate workflow", "POST", "/api/workflow/validate")),
                operation("WorkflowDefinition", "save", "流程定义：保存", "Save workflow", "workflow:edit",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：保存", "Save workflow", "PUT", "/api/workflow/definitions/{id}")),
                operation("WorkflowDefinition", "publish", "流程定义：发布", "Publish workflow", "workflow:publish",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：发布", "Publish workflow", "POST", "/api/workflow/definitions/{id}/publish")),
                operation("WorkflowDefinition", "disable", "流程定义：停用", "Disable workflow", "workflow:publish",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：停用", "Disable workflow", "POST", "/api/workflow/definitions/{id}/disable")),
                operation("WorkflowDefinition", "versions", "流程定义：查看版本", "View versions", "workflow:read",
                        new PageOperation("workflow/designer", "/workflow/designer", "流程定义：查看版本", "View versions", "GET", "/api/workflow/definitions/{id}/versions")),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("WorkflowDefinition", "businessOptions", "查询流程业务能力", "Read workflow business options", ""),
                com.appnexus.contracts.businessobject.DedicatedOperations.operation("WorkflowDefinition", "configurationOptions", "查询流程设计配置", "Read workflow configuration", "")), access);
    }
}
