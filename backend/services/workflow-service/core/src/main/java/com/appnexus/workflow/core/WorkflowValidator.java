package com.appnexus.workflow.core;

import static com.appnexus.workflow.core.WorkflowModel.*;

import java.util.*;

/** 发布前权威校验：限制图规模、循环、输入、分支及结构化并行，禁止导入可执行脚本。 */
public final class WorkflowValidator {
  private WorkflowValidator() {}

  /**
   * 验证定义；失败返回稳定码，由接口映射国际化文案。
   *
   * @param model 草稿模型，最多 100 节点、200 连接；未填必要配置视为不完整
   * @param capabilities 当前租户可用且有权配置的能力标识
   * @param fields 能力声明允许参与流转的字段，含节点决策结果
   * @return 所有已发现错误，不修改草稿
   */
  public static List<String> validate(
      WorkflowModel model, Set<String> capabilities, Set<String> fields) {
    var errors = new ArrayList<String>();
    if (model == null || model.nodes() == null || model.edges() == null)
      return List.of("MODEL_REQUIRED");
    if (model.name() == null
        || model.name().isBlank()
        || model.name().length() > 100
        || model.mode() == null
        || !identifier(model.objectType())
        || !identifier(model.purpose())) errors.add("MODEL_IDENTITY_INVALID");
    if ((model.entryCapability() == null || !capabilities.contains(model.entryCapability())))
      errors.add("ENTRY_CAPABILITY_UNAVAILABLE");
    if (model.nodes().size() < 2 || model.nodes().size() > 100 || model.edges().size() > 200)
      return List.of("GRAPH_SIZE_INVALID");
    var ids = new HashSet<String>();
    for (var n : model.nodes()) {
      if (n == null
          || !identifier(n.id())
          || !ids.add(n.id())
          || n.kind() == null
          || n.name() == null
          || n.name().isBlank()
          || n.name().length() > 100) {
        errors.add("NODE_INVALID");
        continue;
      }
      if (n.position() != null
          && (!Double.isFinite(n.position().x())
              || !Double.isFinite(n.position().y())
              || n.position().x() < 0
              || n.position().y() < 0
              || n.position().x() > 20000
              || n.position().y() > 20000)) errors.add("NODE_POSITION_INVALID:" + n.id());
      if (n.timeoutSeconds() < 60
          || n.timeoutSeconds() > 2592000
          || n.retries() < 0
          || n.retries() > 5) errors.add("NODE_LIMIT_INVALID:" + n.id());
      if (Set.of(Kind.REVIEW, Kind.HANDLE).contains(n.kind())) {
        if (n.people() == null
            || n.people().isEmpty()
            || n.people().size() > 50
            || n.completion() == null) errors.add("PEOPLE_REQUIRED:" + n.id());
        else
          for (var p : n.people())
            if (p == null
                || (p.kind() == null || !Set.of("MEMBER", "LEADER", "POSITION").contains(p.kind()))
                || (!p.kind().equals("LEADER")
                    && (p.reference() == null || p.reference().isBlank())))
              errors.add("PERSON_INVALID:" + n.id());
      }
      if (Set.of(Kind.HANDLE, Kind.ACTION, Kind.WAIT).contains(n.kind())
          && (n.capability() == null || !capabilities.contains(n.capability())))
        errors.add("CAPABILITY_UNAVAILABLE:" + n.id());
      if (n.required() != null && n.required().size() > 100
          || n.input() != null && n.input().size() > 100) errors.add("INPUT_LIMIT:" + n.id());
      if (n.input() != null)
        for (var v : n.input().values())
          if (v == null || (!v.startsWith("literal:") && !fields.contains(v)))
            errors.add("INPUT_REFERENCE_INVALID:" + n.id());
    }
    if (!errors.isEmpty()) return errors;
    var starts = model.nodes().stream().filter(n -> n.kind() == Kind.START).toList();
    if (starts.size() != 1 || model.nodes().stream().noneMatch(n -> n.kind() == Kind.END))
      return List.of("START_END_REQUIRED");
    for (var e : model.edges())
      if (e == null || !ids.contains(e.from()) || !ids.contains(e.to()) || e.from().equals(e.to()))
        errors.add("EDGE_INVALID");
    if (!errors.isEmpty()) return errors;
    if (model.edges().stream().map(e -> e.from() + ":" + e.to()).distinct().count()
        != model.edges().size()) return List.of("DUPLICATE_EDGE");
    for (var n : model.nodes()) {
      var out = model.edges().stream().filter(e -> e.from().equals(n.id())).toList();
      long incoming = model.edges().stream().filter(e -> e.to().equals(n.id())).count();
      if (n.kind() == Kind.START && incoming != 0
          || n.kind() == Kind.END && !out.isEmpty()
          || n.kind() != Kind.END && out.isEmpty()) errors.add("NODE_CONNECTION_INVALID:" + n.id());
      if (n.kind() != Kind.CHOICE
          && n.kind() != Kind.FORK
          && n.kind() != Kind.END
          && out.size() != 1) errors.add("OUTGOING_INVALID:" + n.id());
      if (n.kind() != Kind.JOIN && n.kind() != Kind.MERGE && n.kind() != Kind.END && incoming > 1)
        errors.add("MERGE_REQUIRES_JOIN:" + n.id());
      if (n.kind() == Kind.CHOICE) {
        if (out.size() < 2 || out.stream().filter(Edge::defaultEdge).count() != 1)
          errors.add("CHOICE_DEFAULT_REQUIRED:" + n.id());
        var seen = new HashSet<String>();
        for (var e : out)
          if (!e.defaultEdge()
              && (!fields.contains(e.field())
                  || e.value() == null
                  || e.value().length() > 100
                  || !seen.add(e.field() + "=" + e.value())))
            errors.add("CONDITION_INVALID:" + n.id());
      } else if (out.stream()
          .anyMatch(e -> e.field() != null && !e.field().isBlank() || e.defaultEdge()))
        errors.add("CONDITION_ON_NON_CHOICE:" + n.id());
      if (n.kind() == Kind.FORK
          && (out.size() < 2
              || !ids.contains(n.join())
              || model.node(n.join()).kind() != Kind.JOIN))
        errors.add("FORK_JOIN_REQUIRED:" + n.id());
    }
    var visited = new HashSet<String>();
    var visiting = new HashSet<String>();
    if (cyclic(starts.getFirst().id(), model, visited, visiting)) errors.add("CYCLE_FORBIDDEN");
    if (visited.size() != ids.size()) errors.add("UNREACHABLE_NODE");
    if (errors.contains("CYCLE_FORBIDDEN")) return errors;
    for (var n : model.nodes())
      if (n.kind() == Kind.FORK && ids.contains(n.join())) {
        var branchSets = new ArrayList<Set<String>>();
        for (var edge : model.edges().stream().filter(e -> e.from().equals(n.id())).toList()) {
          var branch = new HashSet<String>();
          if (!allReach(edge.to(), n.join(), model, branch))
            errors.add("FORK_NOT_CONVERGENT:" + n.id());
          if (branch.stream()
              .anyMatch(
                  id -> model.node(id).kind() == Kind.CHOICE || model.node(id).kind() == Kind.END))
            errors.add("FORK_BRANCH_CONTROL_UNSUPPORTED:" + n.id());
          for (var other : branchSets)
            if (!Collections.disjoint(other, branch)) errors.add("FORK_BRANCH_OVERLAP:" + n.id());
          branchSets.add(branch);
        }
        var union = new HashSet<String>();
        branchSets.forEach(union::addAll);
        for (var e : model.edges())
          if (union.contains(e.to()) && !union.contains(e.from()) && !e.from().equals(n.id()))
            errors.add("FORK_EXTERNAL_ENTRY:" + n.id());
      }
    for (var n : model.nodes())
      if (n.kind() == Kind.JOIN
          && model.nodes().stream()
                  .filter(f -> f.kind() == Kind.FORK && n.id().equals(f.join()))
                  .count()
              != 1) errors.add("JOIN_OWNER_REQUIRED:" + n.id());
    if (model.matches() != null)
      for (var m : model.matches())
        if (m == null
            || !fields.contains(m.field())
            || m.value() == null
            || m.value().length() > 100) errors.add("MATCH_INVALID");
    if (model.terminalActions() != null)
      for (var e : model.terminalActions().entrySet())
        if (!Set.of("APPROVED", "REJECTED", "WITHDRAWN").contains(e.getKey())
            || !capabilities.contains(e.getValue())) errors.add("TERMINAL_ACTION_INVALID");
    if (model.mode() == Mode.BUSINESS
        && model.terminalActions() != null
        && !model.terminalActions().isEmpty()) errors.add("BUSINESS_TERMINAL_ACTION_FORBIDDEN");
    if (model.withdrawalCapability() != null
        && !model.withdrawalCapability().isBlank()
        && !capabilities.contains(model.withdrawalCapability()))
      errors.add("WITHDRAWAL_CAPABILITY_UNAVAILABLE");
    if (model.copies() != null) {
      if (model.copies().size() > 50) errors.add("COPIES_LIMIT");
      for (var p : model.copies())
        if (p == null
            || (p.kind() == null || !Set.of("MEMBER", "LEADER", "POSITION").contains(p.kind()))
            || (!p.kind().equals("LEADER") && (p.reference() == null || p.reference().isBlank())))
          errors.add("COPY_RULE_INVALID");
    }
    return List.copyOf(errors);
  }

  private static boolean identifier(String s) {
    return s != null && s.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}");
  }

  private static boolean cyclic(String id, WorkflowModel m, Set<String> done, Set<String> stack) {
    if (stack.contains(id)) return true;
    if (done.contains(id)) return false;
    stack.add(id);
    done.add(id);
    for (var e : m.edges()) if (e.from().equals(id) && cyclic(e.to(), m, done, stack)) return true;
    stack.remove(id);
    return false;
  }

  private static boolean allReach(String id, String join, WorkflowModel m, Set<String> seen) {
    if (id.equals(join)) return true;
    if (!seen.add(id)) return true;
    var out = m.edges().stream().filter(e -> e.from().equals(id)).toList();
    return !out.isEmpty() && out.stream().allMatch(e -> allReach(e.to(), join, m, seen));
  }
}
