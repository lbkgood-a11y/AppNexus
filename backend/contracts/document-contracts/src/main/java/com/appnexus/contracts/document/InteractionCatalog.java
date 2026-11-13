package com.appnexus.contracts.document;
import java.util.List;
/** 跨服务只读契约目录；服务引用同一声明，不另行保存可编辑的业务字段定义。 */
public interface InteractionCatalog {
  /** 当前部署的完整不可变声明，访问控制由管理适配器处理，返回内容无业务数据。 */
  List<InteractionContract> contracts();
}
