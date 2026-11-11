import {
  newNode,
  type BusinessProfile,
  type WorkflowModel,
  type WorkflowNode,
} from "./index";

/** 为后端注册单据创建最小审批图，默认禁止自审；结果处理从该单据目录绑定。 */
export function businessModel(
  capability: string,
  profile: BusinessProfile,
  names: { title: string; start: string; review: string; end: string },
): WorkflowModel {
  const start = newNode("start", "START"),
    review = newNode("review", "REVIEW"),
    end = newNode("end", "END");
  start.name = names.start;
  review.name = names.review;
  end.name = names.end;
  review.people = [{ kind: "LEADER", reference: "" }];
  review.capability = capability;
  return {
    name: names.title,
    objectType: profile.objectType,
    purpose: profile.purpose,
    mode: "APPROVAL",
    entryCapability: capability,
    matches: [],
    nodes: [start, review, end],
    edges: [plainEdge(start.id, review.id), plainEdge(review.id, end.id)],
    terminalActions: Object.fromEntries(
      profile.outcomes
        .filter((o) => o.result !== "WITHDRAWN")
        .map((o) => [o.result, o.capability]),
    ),
    withdrawalCapability: null,
    copies: [],
  };
}

/** 无条件连接不会携带历史条件；修改图时使用新对象避免共享引用。 */
function plainEdge(from: string, to: string) {
  return { from, to, field: null, value: null, defaultEdge: false };
}

/**
 * 在唯一结束节点前追加审批，保留已有条件及默认路径；不改写已发布版本或删除现有节点。
 * @param conditional true 为条件成立才追加审批，false 为所有路径追加审批
 * @param field 条件字段必须来自当前单据目录；新增条件留空值，填写并校验后才能发布
 * @return 新审批节点，无法识别的结构返回 null，不静默重置复杂图
 */
export function appendBusinessReview(
  model: WorkflowModel,
  conditional: boolean,
  names: { review: string; choice: string; merge: string },
  field?: string,
): WorkflowNode | null {
  const ends = model.nodes.filter((n) => n.kind === "END");
  if (ends.length !== 1 || !model.edges.some((e) => e.to === ends[0].id))
    return null;
  const end = ends[0],
    suffix = crypto.randomUUID().slice(0, 8);
  const review = newNode("review_" + suffix, "REVIEW");
  review.name = names.review;
  review.capability = model.entryCapability;
  review.people = [{ kind: "LEADER", reference: "" }];
  if (!conditional) {
    for (const edge of model.edges) if (edge.to === end.id) edge.to = review.id;
    model.nodes.push(review);
    model.edges.push(plainEdge(review.id, end.id));
  } else {
    const choice = newNode("choice_" + suffix, "CHOICE"),
      merge = newNode("merge_" + suffix, "MERGE");
    choice.name = names.choice;
    merge.name = names.merge;
    for (const edge of model.edges) if (edge.to === end.id) edge.to = choice.id;
    model.nodes.push(choice, review, merge);
    model.edges.push(
      {
        from: choice.id,
        to: review.id,
        field: field || "",
        value: "",
        operator: "EQ",
        defaultEdge: false,
      },
      { ...plainEdge(choice.id, merge.id), defaultEdge: true },
      plainEdge(review.id, merge.id),
      plainEdge(merge.id, end.id),
    );
  }
  // 结构变化后清除展示坐标以重新拓扑布局，业务字段和既有条件保持不变。
  for (const node of model.nodes) node.position = null;
  return review;
}

/** 删除可识别的顺序审批或独立条件审批；复杂汇合拒绝修改，至少保留一个审批步骤。 */
export function removeBusinessReview(
  model: WorkflowModel,
  id: string,
): boolean {
  if (model.nodes.filter((n) => n.kind === "REVIEW").length <= 1) return false;
  const incoming = model.edges.filter((e) => e.to === id),
    outgoing = model.edges.filter((e) => e.from === id);
  if (incoming.length !== 1 || outgoing.length !== 1) return false;
  const choice = model.nodes.find(
    (n) => n.id === incoming[0].from && n.kind === "CHOICE",
  );
  const merge = model.nodes.find(
    (n) => n.id === outgoing[0].to && n.kind === "MERGE",
  );
  if (choice) {
    const branches = model.edges.filter((e) => e.from === choice.id),
      exits = model.edges.filter((e) => e.from === merge?.id);
    if (
      !merge ||
      branches.length !== 2 ||
      !branches.some((e) => e.defaultEdge && e.to === merge.id) ||
      model.edges.filter((e) => e.to === merge.id).length !== 2 ||
      exits.length !== 1
    )
      return false;
    for (const edge of model.edges)
      if (edge.to === choice.id) edge.to = exits[0].to;
    const removed = new Set([id, choice.id, merge.id]);
    model.nodes = model.nodes.filter((n) => !removed.has(n.id));
    model.edges = model.edges.filter(
      (e) => !removed.has(e.from) && !removed.has(e.to),
    );
  } else {
    incoming[0].to = outgoing[0].to;
    model.nodes = model.nodes.filter((n) => n.id !== id);
    model.edges = model.edges.filter((e) => e.from !== id);
  }
  for (const node of model.nodes) node.position = null;
  return true;
}
