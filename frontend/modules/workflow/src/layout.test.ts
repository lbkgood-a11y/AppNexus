import { describe, it, expect } from "vitest";
import { layoutGraph } from "./layout";
import { newNode, type Edge } from "./index";
const edge = (from: string, to: string): Edge => ({
  from,
  to,
  field: null,
  value: null,
  defaultEdge: false,
});
/** 验证分支汇合的位置语义和未完成草稿的可编辑性，避免仅做样式快照测试。 */
describe("workflow topology", () => {
  it("places a join after both branches even when storage order is reversed", () => {
    const graph = layoutGraph({
      nodes: [
        newNode("join", "JOIN"),
        newNode("start", "START"),
        newNode("a", "ACTION"),
        newNode("b", "ACTION"),
      ],
      edges: [
        edge("start", "a"),
        edge("start", "b"),
        edge("a", "join"),
        edge("b", "join"),
      ],
    });
    expect(graph.positions.join.x).toBeGreaterThan(graph.positions.a.x);
    expect(graph.positions.a.x).toBe(graph.positions.b.x);
    expect(graph.positions.a.y).not.toBe(graph.positions.b.y);
  });
  it("keeps cyclic and dangling drafts visible without following them forever", () => {
    const graph = layoutGraph({
      nodes: [newNode("a", "ACTION"), newNode("b", "ACTION")],
      edges: [edge("a", "b"), edge("b", "a"), edge("missing", "a")],
    });
    expect(Object.keys(graph.positions)).toHaveLength(2);
    expect(graph.positions.a.x).not.toBe(graph.positions.b.x);
    expect(Number.isFinite(graph.width)).toBe(true);
  });
});
