package com.appnexus.bulkwarehousing.domain.stock;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 按已确认转换关系查询批次来源及去向，适用于经授权读取的领域事实。 */
public final class BatchLineage {
  /**
   * @param source 来源库存身份
   * @param output 对应产出库存身份
   */
  public record Edge(String source, String output) {}

  private final Map<String, Set<String>> upstream = new HashMap<>();
  private final Map<String, Set<String>> downstream = new HashMap<>();

  /**
   * @param edges 已确认且适用的投入至产出关系
   * @throws IllegalArgumentException 存在重复关系或循环时抛出
   */
  public BatchLineage(Collection<Edge> edges) {
    for (var edge : edges) {
      if (edge.source().equals(edge.output()) || outputs(edge.output()).contains(edge.source())
          || !downstream.computeIfAbsent(edge.source(), ignored -> new HashSet<>()).add(edge.output())) {
        throw new IllegalArgumentException("批次谱系关系重复或形成循环");
      }
      upstream.computeIfAbsent(edge.output(), ignored -> new HashSet<>()).add(edge.source());
    }
  }

  /**
   * @param id 目标库存身份
   * @return 可追溯的直接和间接来源身份集合
   */
  public Set<String> sources(String id) { return traverse(id, upstream); }

  /**
   * @param id 来源库存身份
   * @return 可追溯的直接和间接产出身份集合
   */
  public Set<String> outputs(String id) { return traverse(id, downstream); }

  private static Set<String> traverse(String id, Map<String, Set<String>> graph) {
    var found = new HashSet<String>();
    var pending = new ArrayDeque<>(graph.getOrDefault(id, Set.of()));
    while (!pending.isEmpty()) {
      var next = pending.removeFirst();
      if (found.add(next)) pending.addAll(graph.getOrDefault(next, Set.of()));
    }
    return Set.copyOf(found);
  }
}
