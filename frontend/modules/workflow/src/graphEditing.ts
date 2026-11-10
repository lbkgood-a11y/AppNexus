import type { WorkflowModel } from "./index";
/** 交互连线的即时约束；完整选人、并行与业务校验仍由后端发布校验负责。 */
export function connectionError(
  model: WorkflowModel,
  from: string,
  to: string,
): string | null {
  const a = model.nodes.find((n) => n.id === from),
    b = model.nodes.find((n) => n.id === to);
  if (!a || !b || from === to) return "EDGE_INVALID";
  if (a.kind === "END" || b.kind === "START") return "NODE_CONNECTION_INVALID";
  if (model.edges.some((e) => e.from === from && e.to === to))
    return "DUPLICATE_EDGE";
  if (
    !["CHOICE", "FORK"].includes(a.kind) &&
    model.edges.some((e) => e.from === from)
  )
    return "OUTGOING_INVALID";
  if (
    !["JOIN", "MERGE", "END"].includes(b.kind) &&
    model.edges.some((e) => e.to === to)
  )
    return "MERGE_REQUIRES_JOIN";
  const stack = [to],
    seen = new Set<string>();
  while (stack.length) {
    const id = stack.pop()!;
    if (id === from) return "CYCLE_FORBIDDEN";
    if (seen.has(id)) continue;
    seen.add(id);
    model.edges.filter((e) => e.from === id).forEach((e) => stack.push(e.to));
  }
  return null;
}
/** 坐标限制在安全画布范围，拖动预览不持久化，松手后一次提交以形成一条撤销记录。 */
export function canvasPoint(x: number, y: number) {
  return {
    x: Math.max(0, Math.min(20000, Math.round(x))),
    y: Math.max(0, Math.min(20000, Math.round(y))),
  };
}
