package com.appnexus.printing.adapter.jdbc;

/** 打印 API 稳定错误；只暴露业务码和 HTTP 状态，不携带模板数据或设备内部异常。 */
final class PrintFailure extends com.appnexus.contracts.printing.PrintRejection {

  PrintFailure(String code, int status) {
    super(code,status);
  }

}
