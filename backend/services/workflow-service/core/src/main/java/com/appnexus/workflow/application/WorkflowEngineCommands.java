package com.appnexus.workflow.application;
import java.util.Map;
/** 工作流业务需要的窄引擎输出端口，不暴露Flowable查询器或委托对象。 */
public interface WorkflowEngineCommands {
 /** @param instance 可信持久化引擎实例标识 @param fields 已验证节点字段；不接受引擎变量名 */
 void fields(String instance,Map<String,String> fields);
 /** @param instance 可信实例 @param result 原固定流程结果，不由浏览器任意写变量 */
 void result(String instance,String result);
 /** @param instance 可信实例 @param reason 原固定终止原因 */
 void terminate(String instance,String reason);
 /** @param task 原已授权引擎任务标识 */
 void complete(String task);
 /** @param task 原任务标识 @param actor 可信目标账号，null表示原租约回收解除分配 */
 void assign(String task,String actor);
 /** @param execution 原可靠回调保存的执行标识，不能由外部选择 */
 void trigger(String execution);
 /** @param instance 原引擎实例 @return 当前运行实例是否仍存在 */
 boolean running(String instance);
 /** @param instance 原引擎实例 @return 固定wf_fields变量，不开放任意变量读取 */
 Map<String,String> fields(String instance);
 /** @param tenant 可信租户 @param key 固定生成的部署键 @param bpmn 经模型校验和受控编译的BPMN字节 @return 同租户新部署的引擎定义标识 */
 String deploy(String tenant,String key,byte[] bpmn);
 /** @param definition 已发布固定引擎定义 @param businessKey 当前业务实例键 @param variables 固定wf变量集合，禁止客户端指定Java类型 @return 新引擎实例标识 */
 String start(String definition,String businessKey,java.util.Map<String,Object> variables);
 /** @param tenant 固定租户 @param task 原持久化任务标识 @return 同租户引擎任务是否存在 */
 boolean taskExists(String tenant,String task);
}
