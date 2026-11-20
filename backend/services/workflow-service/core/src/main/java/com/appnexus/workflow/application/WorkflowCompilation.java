package com.appnexus.workflow.application;
import com.appnexus.workflow.core.WorkflowModel;
/** 受控工作流模型编译输出端口，不允许外部BPMN或任意委托Bean。 */
public interface WorkflowCompilation {
 /** @param key 原服务生成的固定部署键 @param model 已通过纯规则验证的模型 @return 仅含固定内部委托的BPMN字节 */
 byte[] compile(String key,WorkflowModel model);
}
