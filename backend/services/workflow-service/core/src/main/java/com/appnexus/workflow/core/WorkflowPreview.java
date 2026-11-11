package com.appnexus.workflow.core;

import static com.appnexus.workflow.core.WorkflowModel.*;

import java.util.*;

/** 受控图的无副作用预演；调用者提供样本输出，不调用业务或引擎，不代表真实业务验收。 */
public final class WorkflowPreview {
  private WorkflowPreview() {}

  /** 返回样本路径，最多1000步；必须先通过WorkflowValidator，样本不得包含敏感数据。 */
  public static List<String> trace(WorkflowModel m, Map<String, String> sample) {
    var queue = new ArrayDeque<String>();
    queue.add(
        m.nodes().stream().filter(n -> n.kind() == Kind.START).findFirst().orElseThrow().id());
    var joins = new HashMap<String, Integer>();
    var result = new ArrayList<String>();
    while (!queue.isEmpty()) {
      if (result.size() > 1000) throw new IllegalArgumentException("PREVIEW_LIMIT");
      String id = queue.removeFirst();
      var n = m.node(id);
      if (n.kind() == Kind.JOIN) {
        int expected = (int) m.edges().stream().filter(e -> e.to().equals(id)).count();
        int count = joins.merge(id, 1, Integer::sum);
        if (count < expected) continue;
      }
      result.add(id);
      var out = m.edges().stream().filter(e -> e.from().equals(id)).toList();
      if (n.kind() == Kind.CHOICE) {
        var chosen =
            out.stream()
                .filter(e -> !e.defaultEdge() && WorkflowCondition.test(sample, e.field(), e.operator(), e.value()))
                .findFirst()
                .orElseGet(() -> out.stream().filter(Edge::defaultEdge).findFirst().orElseThrow());
        queue.add(chosen.to());
      } else out.forEach(e -> queue.add(e.to()));
    }
    return List.copyOf(result);
  }
}
