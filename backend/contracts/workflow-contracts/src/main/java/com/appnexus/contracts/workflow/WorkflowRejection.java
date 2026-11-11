package com.appnexus.contracts.workflow;

/** 公开工作流调用的稳定拒绝；业务适配器可映射错误，无需依赖引擎实现或暴露异常原文。 */
public class WorkflowRejection extends RuntimeException {
  private final String code;
  private final int status;

  /**
   * @param code 稳定业务码，不包含单据原文或基础设施错误
   * @param status HTTP 业务拒绝状态，仅允许 400、403、404、409
   */
  public WorkflowRejection(String code, int status) {
    super(code);
    if (!java.util.Set.of(400, 403, 404, 409).contains(status))
      throw new IllegalArgumentException("WORKFLOW_STATUS_INVALID");
    this.code = code;
    this.status = status;
  }

  /** @return 非敏感稳定业务错误码 */
  public String code() { return code; }

  /** @return 业务拒绝对应 HTTP 状态 */
  public int status() { return status; }
}
