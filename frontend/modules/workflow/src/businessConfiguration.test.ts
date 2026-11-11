import { describe, expect, it } from "vitest";
import { appendBusinessReview, businessModel, removeBusinessReview } from "./businessConfiguration";
import type { BusinessProfile } from "./index";

const profile: BusinessProfile = {
  objectType: "LeaveRequest",
  purpose: "approval",
  nameZh: "请假单",
  nameEn: "Leave",
  descriptionZh: "",
  descriptionEn: "",
  fields: [],
  withdrawalCapability: null,
  outcomes: [
    {
      result: "APPROVED",
      capability: "leavePoc",
      nameZh: "通过",
      nameEn: "Approve",
    },
  ],
};
const model = () =>
  businessModel("leavePoc", profile, {
    title: "请假审批",
    start: "开始",
    review: "主管",
    end: "结束",
  });
const names = { review: "人事", choice: "天数", merge: "汇合" };

describe("business approval graph updates", () => {
  it("removes an independent conditional step and keeps the original approval connected", () => {
    const flow = model(); const review = appendBusinessReview(flow, true, names, "days")!;
    expect(removeBusinessReview(flow, review.id)).toBe(true);
    expect(flow.nodes.map(n => n.id)).toEqual(["start", "review", "end"]);
    expect(flow.edges.map(e => [e.from, e.to])).toEqual([["start", "review"], ["review", "end"]]);
    expect(removeBusinessReview(flow, "review")).toBe(false);
  });
  it("preserves existing approval and creates a default path around conditional review", () => {
    const flow = model();
    const review = appendBusinessReview(flow, true, names, "days")!;
    const condition = flow.edges.find((e) => e.to === review.id)!;
    expect(condition.field).toBe("days");
    expect(
      flow.edges.filter((e) => e.from === condition.from && e.defaultEdge),
    ).toHaveLength(1);
    const merge = flow.edges.find((e) => e.from === review.id)!.to;
    expect(
      flow.edges.find((e) => e.from === condition.from && e.defaultEdge)?.to,
    ).toBe(merge);
    expect(flow.nodes.find((n) => n.id === "review")?.people).toEqual([
      { kind: "LEADER", reference: "" },
    ]);
    expect(flow.nodes.every((n) => !n.allowSelf)).toBe(true);
    expect(flow.terminalActions.APPROVED).toBe("leavePoc");
  });
  it("appends common approval after both conditional paths without losing conditions", () => {
    const flow = model();
    appendBusinessReview(flow, true, names, "days");
    const condition = flow.edges.find((e) => e.field === "days")!;
    condition.value = "3";
    condition.operator = "GT";
    const common = appendBusinessReview(flow, false, names)!;
    expect(flow.edges.filter((e) => e.to === "end").map((e) => e.from)).toEqual(
      [common.id],
    );
    expect(condition).toMatchObject({
      field: "days",
      value: "3",
      operator: "GT",
    });
  });
  it("does not mutate a multi-end graph that the form cannot append safely", () => {
    const flow = model();
    flow.nodes.push({ ...flow.nodes[2], id: "otherEnd" });
    const before = JSON.stringify(flow);
    expect(appendBusinessReview(flow, true, names)).toBeNull();
    expect(JSON.stringify(flow)).toBe(before);
  });
});
