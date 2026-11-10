package com.appnexus.contracts.workflow;

import java.util.Map;

/** 可信业务适配器的异步结果入口；实现方必须先认证外部回调并绑定能力，不直接暴露浏览器成功声明。 */
public interface WorkflowResults {
  /** 同租户同调用标识幂等确认；能力不匹配、结果冲突或未等待均拒绝。 */
  void callback(String tenant, String jobId, String capability, Map<String, String> outputs);
}
