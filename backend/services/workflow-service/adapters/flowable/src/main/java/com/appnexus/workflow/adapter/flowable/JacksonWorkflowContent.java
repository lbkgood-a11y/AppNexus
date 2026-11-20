package com.appnexus.workflow.adapter.flowable;
import com.appnexus.workflow.application.WorkflowContent;
import com.appnexus.workflow.core.WorkflowModel;
import com.appnexus.contracts.data.DataContract;
import java.util.Map;
/** 原工作流JSON技术适配；保留键排序及错误契约，禁止客户端提供目标类。 */
public final class JacksonWorkflowContent implements WorkflowContent {
 public String encode(Object value){return WorkflowJson.write(value);}
 public WorkflowModel model(String value){return WorkflowJson.read(value,WorkflowModel.class);}
 public DataContract schema(String value){return WorkflowJson.read(value,DataContract.class);}
 public Object response(String value){return WorkflowJson.read(value,Object.class);}
 public Map<String,String> fields(String value){return WorkflowJson.map(value);}
}
