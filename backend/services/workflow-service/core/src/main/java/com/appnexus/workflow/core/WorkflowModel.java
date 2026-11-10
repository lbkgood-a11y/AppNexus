package com.appnexus.workflow.core;

import java.util.*;

/** 工作流受控定义；公开数据只表达配置，不执行任意表达式或业务代码。 */
public record WorkflowModel(
    String name,
    String objectType,
    String purpose,
    Mode mode,
    String entryCapability,
    List<Match> matches,
    List<Node> nodes,
    List<Edge> edges,
    Map<String, String> terminalActions,
    List<Person> copies,
    String withdrawalCapability) {
  /** 兼容内部构造的无抄送/撤回模型；外部JSON缺省字段同样按未启用处理。 */
  public WorkflowModel(
      String name,
      String objectType,
      String purpose,
      Mode mode,
      String entryCapability,
      List<Match> matches,
      List<Node> nodes,
      List<Edge> edges,
      Map<String, String> terminalActions) {
    this(
        name,
        objectType,
        purpose,
        mode,
        entryCapability,
        matches,
        nodes,
        edges,
        terminalActions,
        List.of(),
        null);
  }

  /** APPROVAL 结束后投递结果；BUSINESS 在业务节点完成后结束。 */
  public enum Mode {
    APPROVAL,
    BUSINESS
  }

  /** 首批节点；复杂子流程由后续契约扩展，不接受任意 BPMN 导入。 */
  public enum Kind {
    START,
    END,
    REVIEW,
    HANDLE,
    ACTION,
    WAIT,
    CHOICE,
    MERGE,
    FORK,
    JOIN
  }

  /** 多人完成策略；办理节点与审核节点复用执行机制。 */
  public enum Completion {
    ALL,
    ANY,
    SEQUENTIAL
  }

  /** 选人规则；reference 使用稳定标识，allowSelf 默认 false。 */
  public record Person(String kind, String reference) {}

  /** 等值匹配条件，值来自业务快照；空集合表示适用所有组织/字段。 */
  public record Match(String field, String value) {}

  /**
   * 节点声明。
   *
   * @param retries 自动动作最大重试次数，0—5 次，不含首次执行
   * @param timeoutSeconds 等待结果或任务期限，60—2592000 秒，超时只转异常不自动通过
   * @param capability 受控能力标识；HANDLE/ACTION/WAIT 必填
   * @param join 对应汇合节点标识，仅 FORK 必填
   * @param separate HANDLE 为 true 表示每人分别办理，false 表示共享内容仍由业务端并发校验
   */
  public record Node(
      String id,
      String name,
      Kind kind,
      List<Person> people,
      Completion completion,
      boolean allowSelf,
      String capability,
      Set<String> required,
      Map<String, String> input,
      int retries,
      int timeoutSeconds,
      String join,
      boolean separate) {}

  /** 连接；CHOICE 使用 field/value 等值条件，defaultEdge 只允许一条；字段名受服务端白名单校验。 */
  public record Edge(String from, String to, String field, String value, boolean defaultEdge) {}

  /** 返回指定节点，未知节点立即拒绝，避免配置错误静默跳过。 */
  public Node node(String id) {
    return nodes.stream()
        .filter(n -> n.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_NODE"));
  }
}
