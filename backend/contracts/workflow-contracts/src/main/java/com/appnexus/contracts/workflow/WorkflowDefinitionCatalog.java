package com.appnexus.contracts.workflow;

import java.util.*;

/** 跨模块只读发布目录；只返回本租户与指定业务契约兼容的流程，不泄漏模型内部实现。 */
public interface WorkflowDefinitionCatalog {
  /** 流程发布选项，version 为具体正整数；定义停用后不允许新绑定/启动。 */
  record Option(String id, int version, String name) {}
  /** 从业务可信上下文查询兼容的发布流程，所需终态动作必须属于 entry。 */
  /**
   * 可信业务服务精确检查绑定目标，只返回布尔值，不向浏览器开放流程目录。
   * @param context 已验证的租户及有效业务主体
   * @param type 代码登记的对象类型
   * @param purpose 审批用途
   * @param entry 业务入口能力
   * @param outcomes 必须支持的终态
   * @param definitionId 已发布配置中固定的流程标识，不接受客户端任意探测
   * @param version 固定发布版本，正整数
   * @return 目标仍启用且与业务契约兼容；基础设施故障继续抛出
   */
  default boolean available(WorkflowContext context,String type,String purpose,String entry,Set<String> outcomes,String definitionId,int version){
    return version>0&&published(context,type,purpose,entry,outcomes).stream().anyMatch(option->option.id().equals(definitionId)&&option.version()==version);
  }
  List<Option> published(WorkflowContext context, String type, String purpose, String entry, Set<String> outcomes);
}
