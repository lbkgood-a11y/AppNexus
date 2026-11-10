package com.appnexus.workflow.adapter.flowable;

/** 稳定工作流错误；接口不暴露引擎、SQL、脚本或业务异常原文。 */
public final class WorkflowFailure extends RuntimeException {
  private final String code;
  private final int status;

  /**
   * @param code 稳定错误码，不含业务敏感值 @param status HTTP状态，仅400/403/404/409可用于业务拒绝
   */
  public WorkflowFailure(String code, int status) {
    super(code);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
