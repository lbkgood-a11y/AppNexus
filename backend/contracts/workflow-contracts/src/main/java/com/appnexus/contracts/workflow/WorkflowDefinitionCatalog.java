package com.appnexus.contracts.workflow;

import java.util.*;

/** 跨模块只读发布目录；只返回本租户与指定业务契约兼容的流程，不泄漏模型内部实现。 */
public interface WorkflowDefinitionCatalog {
  /** 流程发布选项，version 为具体正整数；定义停用后不允许新绑定/启动。 */
  record Option(String id, int version, String name) {}
  /** 从业务可信上下文查询兼容的发布流程，所需终态动作必须属于 entry。 */
  List<Option> published(WorkflowContext context, String type, String purpose, String entry, Set<String> outcomes);
}
