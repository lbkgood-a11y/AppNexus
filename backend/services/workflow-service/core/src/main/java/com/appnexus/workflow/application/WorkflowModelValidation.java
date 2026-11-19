package com.appnexus.workflow.application;

import com.appnexus.contracts.workflow.WorkflowBusiness;
import com.appnexus.contracts.workflow.WorkflowBusinessProfile;
import com.appnexus.workflow.core.*;
import static com.appnexus.workflow.core.WorkflowModel.*;
import java.util.*;

/** 草稿校验应用服务；协调核心图规则与已授权能力事实，不依赖引擎、JSON、数据库或 Spring。 */
public final class WorkflowModelValidation {
  private WorkflowModelValidation() {}
  /**
   * 执行只读校验；不发布、部署或创建任务，结果不能代替执行时授权。
   * @param m 原草稿，允许 null 以返回标准模型错误
   * @param caps 当前主体可用能力快照，包含已验证输入输出白名单
   * @param profiles 同一代码目录生成的业务审批视图，不接受客户端伪造字段和终态
   * @return 保持原顺序的校验错误码；空集合仅表示当前模型检查通过
   */
  public static List<String> validate(WorkflowModel m, List<WorkflowBusiness.Capability> caps,
      Map<String, WorkflowBusinessProfile> profiles) {
    var keys = new HashSet<String>();
    var fields = new HashSet<String>();
    caps.forEach(
        b -> {
          keys.add(b.key());
          fields.addAll(b.outputs());
        });
    if (m != null && m.nodes() != null)
      m.nodes().stream().filter(Objects::nonNull).forEach(n -> fields.add(n.id() + "_decision"));
    var errors = new ArrayList<>(WorkflowValidator.validate(m, keys, fields));
    if (m != null && m.edges() != null) {
      // 数值条件必须显式来自本单据的 NUMBER 字段，避免按字符串或跨业务字段误判。
      var profile = profiles.get(m.entryCapability());
      if (profile != null && m.mode() == Mode.APPROVAL) {
        for (String result : List.of("APPROVED", "REJECTED")) {
          String bound = m.terminalActions() == null ? null : m.terminalActions().get(result);
          if (profile.outcomes().stream().noneMatch(o -> o.result().equals(result) && o.capability().equals(bound)))
            errors.add("BUSINESS_OUTCOME_REQUIRED:" + result);
        }
        if (m.withdrawalCapability() != null && !m.withdrawalCapability().isBlank()) {
          String bound = m.terminalActions() == null ? null : m.terminalActions().get("WITHDRAWN");
          if (!m.withdrawalCapability().equals(profile.withdrawalCapability()) || profile.outcomes().stream()
              .noneMatch(o -> o.result().equals("WITHDRAWN") && o.capability().equals(bound)))
            errors.add("BUSINESS_WITHDRAWAL_INVALID");
        }
      }
      for (var edge : m.edges())
        if (edge != null && !edge.defaultEdge()
            && !Set.of("EQ", "NE").contains(WorkflowCondition.operator(edge.operator()))
            && (profile == null || profile.fields().stream()
                .noneMatch(f -> f.key().equals(edge.field()) && "NUMBER".equals(f.type()))))
          errors.add("NUMERIC_FIELD_REQUIRED:" + edge.from());
    }
    if (m != null && m.nodes() != null)
      for (var n : m.nodes())
        if (n != null && n.capability() != null && !n.capability().isBlank()) {
          if (!keys.contains(n.capability())) {
            errors.add("CAPABILITY_UNAVAILABLE:" + n.id());
            continue;
          }
          var cap = caps.stream().filter(value -> value.key().equals(n.capability())).findFirst().orElseThrow();
          if (n.input() != null && !cap.inputs().containsAll(n.input().keySet()))
            errors.add("INPUT_FIELD_INVALID:" + n.id());
          if (n.required() != null && !cap.inputs().containsAll(n.required()))
            errors.add("REQUIRED_FIELD_INVALID:" + n.id());
        }
    return errors;
  }

}
