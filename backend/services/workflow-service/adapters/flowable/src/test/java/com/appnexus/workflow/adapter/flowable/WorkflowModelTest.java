package com.appnexus.workflow.adapter.flowable;

import static com.appnexus.workflow.core.WorkflowModel.*;
import static org.assertj.core.api.Assertions.*;

import com.appnexus.workflow.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 受控模型失败关闭测试；错误配置不能被编译成可执行脚本或死等并行图。 */
class WorkflowModelTest {
  private Node n(String id, Kind kind) {
    return new Node(
        id,
        id,
        kind,
        List.of(),
        Completion.ALL,
        false,
        null,
        Set.of(),
        Map.of(),
        2,
        3600,
        null,
        true);
  }

  private Edge e(String from, String to) {
    return new Edge(from, to, null, null, false);
  }

  private WorkflowModel m(List<Node> nodes, List<Edge> edges) {
    return new WorkflowModel(
        "模型", "Document", "Review", Mode.BUSINESS, "entry", List.of(), nodes, edges, Map.of());
  }

  @Test
  void cycleAndUnreachableNodesCannotPublish() {
    var model =
        m(
            List.of(n("start", Kind.START), n("a", Kind.MERGE), n("end", Kind.END)),
            List.of(e("start", "a"), e("a", "start")));
    assertThat(WorkflowValidator.validate(model, Set.of("entry"), Set.of()))
        .contains("CYCLE_FORBIDDEN", "UNREACHABLE_NODE");
  }

  @Test
  void duplicateConnectionsCannotCreateExtraTokens() {
    var model =
        m(
            List.of(n("start", Kind.START), n("end", Kind.END)),
            List.of(e("start", "end"), e("start", "end")));
    assertThat(WorkflowValidator.validate(model, Set.of("entry"), Set.of()))
        .contains("DUPLICATE_EDGE");
  }

  @Test
  void arbitraryExpressionIsNotAnInputReference() {
    var action =
        new Node(
            "action",
            "action",
            Kind.ACTION,
            List.of(),
            Completion.ALL,
            false,
            "entry",
            Set.of(),
            Map.of("field", "${T(java.lang.Runtime).getRuntime()}"),
            2,
            3600,
            null,
            true);
    var model =
        m(
            List.of(n("start", Kind.START), action, n("end", Kind.END)),
            List.of(e("start", "action"), e("action", "end")));
    assertThat(WorkflowValidator.validate(model, Set.of("entry"), Set.of()))
        .contains("INPUT_REFERENCE_INVALID:action");
  }

  @Test
  void nodeLimitsAndMissingCapabilityAreRejected() {
    var action =
        new Node(
            "action",
            "action",
            Kind.ACTION,
            List.of(),
            Completion.ALL,
            false,
            null,
            Set.of(),
            Map.of(),
            99,
            1,
            null,
            true);
    var model =
        m(
            List.of(n("start", Kind.START), action, n("end", Kind.END)),
            List.of(e("start", "action"), e("action", "end")));
    assertThat(WorkflowValidator.validate(model, Set.of("entry"), Set.of()))
        .contains("CAPABILITY_UNAVAILABLE:action", "NODE_LIMIT_INVALID:action");
  }

  @Test
  void canvasCoordinatesRoundTripWithoutChangingExecutableBpmn() {
    var original =
        m(List.of(n("start", Kind.START), n("end", Kind.END)), List.of(e("start", "end")));
    String json =
        WorkflowJson.write(original)
            .replace("\"position\":null", "\"position\":{\"x\":240,\"y\":360}");
    var moved = WorkflowJson.read(json, WorkflowModel.class);
    assertThat(moved.node("start").position()).isEqualTo(new Position(240, 360));
    assertThat(WorkflowCompiler.compile("same", original))
        .isEqualTo(WorkflowCompiler.compile("same", moved));
    assertThatThrownBy(() -> new Position(-1, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
