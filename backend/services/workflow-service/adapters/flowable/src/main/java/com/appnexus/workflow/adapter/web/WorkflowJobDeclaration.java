package com.appnexus.workflow.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 流程作业专用入口的唯一对象声明；业务校验与权限执行沿用原用例。 */
@Component
@ConditionalOnProperty(name = "app.workflow.engine.enabled", havingValue = "true")
public final class WorkflowJobDeclaration implements Provider {
    /** @return 已挂接到原页面及 API 的对象与操作契约 */
    @Override
    public Descriptor descriptor() {
        var access = new DataAccess("TENANT", List.of(
                new DataResource("workflow.job", "", "流程作业", "Workflow jobs", List.of())), Map.of());
        return new Descriptor("WorkflowJob", "流程作业", "Workflow Job", "workflow", 2, Category.LEDGER, List.of(), List.of(
                operation("WorkflowJob", "retry", "流程作业：重试", "Retry job", "workflow:operate",
                        new PageOperation("workflow/index", "/workflow/center", "流程作业：重试", "Retry job", "POST", "/api/workflow/jobs/{id}/retry")), scheduledProcessing()), access);
    }
    private Operation scheduledProcessing(){
        String id=com.appnexus.workflow.api.WorkflowManagement.PROCESS_JOBS;
        var interaction=new com.appnexus.contracts.businessobject.InteractionContract(id,1,"WorkflowJob","process",
            "处理流程作业","Process workflow jobs",com.appnexus.contracts.businessobject.InteractionContract.Trigger.SCHEDULE,List.of());
        return new Operation("process","处理流程作业","Process workflow jobs",
            com.appnexus.workflow.api.WorkflowManagement.JOB_WORKER_AUTHORITY,id,null,List.of(),interaction);
    }
}
