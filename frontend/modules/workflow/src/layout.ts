import type { WorkflowModel } from "./index";
/** 画布拓扑布局；纯展示结果不写回流程模型，不改变发布或执行语义。 */
export interface GraphLayout {
  positions: Record<string, { x: number; y: number }>;
  width: number;
  height: number;
}
/**
 * 按最长前驱路径分层，分支在同层纵向排列；未完成/循环草稿仍可显示，不导致编辑器死循环。
 * @param model 未必通过服务端校验的草稿，未知连接端点忽略
 * @return 节点左上角坐标（像素）与内容尺寸；间距只用于图形排版
 */
export function layoutGraph(
  model: Pick<WorkflowModel, "nodes" | "edges">,
): GraphLayout {
  const ids = model.nodes.map((n) => n.id),
    known = new Set(ids),
    incoming = new Map(ids.map((id) => [id, 0])),
    depth = new Map(ids.map((id) => [id, 0]));
  const edges = model.edges.filter((e) => known.has(e.from) && known.has(e.to));
  edges.forEach((e) => incoming.set(e.to, (incoming.get(e.to) || 0) + 1));
  const queue = ids.filter((id) => incoming.get(id) === 0),
    visited = new Set<string>();
  while (queue.length) {
    const id = queue.shift()!;
    if (visited.has(id)) continue;
    visited.add(id);
    for (const e of edges.filter((e) => e.from === id)) {
      depth.set(e.to, Math.max(depth.get(e.to) || 0, (depth.get(id) || 0) + 1));
      incoming.set(e.to, (incoming.get(e.to) || 0) - 1);
      if (incoming.get(e.to) === 0) queue.push(e.to);
    }
  }
  let fallback = Math.max(0, ...depth.values()) + 1;
  for (const id of ids) if (!visited.has(id)) depth.set(id, fallback++);
  const layers = new Map<number, string[]>();
  for (const id of ids) {
    const column = depth.get(id) || 0;
    layers.set(column, [...(layers.get(column) || []), id]);
  }
  const rows = Math.max(1, ...[...layers.values()].map((v) => v.length)),
    positions: GraphLayout["positions"] = {};
  for (const [column, nodes] of layers)
    nodes.forEach(
      (id, row) =>
        (positions[id] = {
          x: 40 + column * 240,
          y: 40 + row * 120 + (rows - nodes.length) * 60,
        }),
    );
  for (const node of model.nodes) {
    const p = node.position;
    if (
      p &&
      Number.isFinite(p.x) &&
      Number.isFinite(p.y) &&
      p.x >= 0 &&
      p.y >= 0 &&
      p.x <= 20000 &&
      p.y <= 20000
    )
      positions[node.id] = { ...p };
  }
  return {
    positions,
    width: Math.max(
      520,
      ...Object.values(positions).map((p) => p.x + 230),
      (Math.max(0, ...layers.keys()) + 1) * 240 + 40,
    ),
    height: Math.max(
      300,
      ...Object.values(positions).map((p) => p.y + 120),
      rows * 120 + 40,
    ),
  };
}
