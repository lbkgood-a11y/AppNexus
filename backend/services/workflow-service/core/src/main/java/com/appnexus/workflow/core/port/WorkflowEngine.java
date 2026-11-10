package com.appnexus.workflow.core.port;

/** 工作流执行引擎端口。运行时类型属于工作流服务内部，不能作为跨服务契约暴露。 当前仅提供引擎标识；流程操作须在领域、权限和持久化契约确定后逐项增加。 */
public interface WorkflowEngine {
  /**
   * 返回执行引擎名称，不访问业务数据，也不表示工作流功能已就绪。
   *
   * @return 稳定的引擎名称
   */
  String engineName();
}
