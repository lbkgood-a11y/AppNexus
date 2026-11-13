package com.appnexus.contracts.printing;
/** 打印公开错误，不包含原文；业务适配器无需依赖打印内部异常类型即可映射 HTTP。 */
public class PrintRejection extends RuntimeException {
  private final String code;private final int status;
  public PrintRejection(String code,int status){super(code);this.code=code;this.status=status;}
  public String code(){return code;}
  public int status(){return status;}
}
