package com.appnexus.workflow.adapter.flowable;
import com.appnexus.workflow.application.WorkflowCompilation;
import com.appnexus.workflow.core.WorkflowModel;
/** 固定模型到Flowable BPMN的技术编译适配，不接受模型自定义类或脚本。 */
public final class FlowableWorkflowCompilation implements WorkflowCompilation {
 public byte[] compile(String key,WorkflowModel model){return WorkflowCompiler.compile(key,model);}
}
