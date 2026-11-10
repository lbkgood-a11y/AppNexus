import { describe, it, expect } from "vitest";
import { connectionError, canvasPoint } from "./graphEditing";
import { emptyModel, newNode } from "./index";
import { layoutGraph } from "./layout";
/** 拖线合法性和缩放后的坐标边界，不能以可点击画布代替流程约束验证。 */
describe("graph editing", () => {
  it("rejects self links, duplicate links, end outputs and start inputs", () => {
    const m = emptyModel();
    expect(connectionError(m, "start", "start")).toBe("EDGE_INVALID");
    expect(connectionError(m, "start", "end")).toBe("DUPLICATE_EDGE");
    expect(connectionError(m, "end", "start")).toBe("NODE_CONNECTION_INVALID");
  });
  it("rejects loops and excess outgoing connections", () => {
    const m = emptyModel();
    m.nodes = [
      newNode("a", "ACTION"),
      newNode("b", "ACTION"),
      newNode("c", "MERGE"),
    ];
    m.edges = [
      { from: "a", to: "b", field: null, value: null, defaultEdge: false },
    ];
    expect(connectionError(m, "a", "c")).toBe("OUTGOING_INVALID");
    expect(connectionError(m, "b", "a")).toBe("CYCLE_FORBIDDEN");
    expect(connectionError(m, "b", "c")).toBeNull();
  });
  it("preserves manually placed nodes through JSON reload and topology changes", () => {
    const m = emptyModel();
    m.nodes[0].position = { x: 320, y: 260 };
    const restored = JSON.parse(JSON.stringify(m));
    expect(layoutGraph(restored).positions.start).toEqual({ x: 320, y: 260 });
    restored.edges = [];
    expect(layoutGraph(restored).positions.start).toEqual({ x: 320, y: 260 });
    expect(canvasPoint(-100, 25000)).toEqual({ x: 0, y: 20000 });
  });
});
