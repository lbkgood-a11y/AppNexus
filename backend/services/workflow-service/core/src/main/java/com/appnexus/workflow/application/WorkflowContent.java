package com.appnexus.workflow.application;
import java.util.Map;
import com.appnexus.workflow.core.WorkflowModel;
import com.appnexus.contracts.data.DataContract;
/** 工作流固定存储内容端口，业务不得请求任意Java目标类型。 */
public interface WorkflowContent {
 /** @param value 服务端固定模型、快照或摘要 @return 原排序配置的JSON，不改变请求指纹 */
 String encode(Object value);
 /** @param value 原已验证持久化模型JSON @return 纯工作流模型 */
 WorkflowModel model(String value);
 /** @param value 原冻结契约JSON @return 稳定数据契约，不加载多态类型 */
 DataContract schema(String value);
 /** @param value 原响应JSON @return JDK对象、集合及标量 */
 Object response(String value);
 /** @param value 原字段快照或交付载荷 @return 固定字符串字段集合 */
 Map<String,String> fields(String value);
}
