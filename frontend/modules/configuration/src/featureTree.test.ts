import { describe, expect, it } from "vitest";
import { buildFeatureTree } from "./featureTree";
import type { FeatureRegistration } from "./index";
const entries: FeatureRegistration[] = [
  { page: { key: "menus", path: "/menus", titleZh: "菜单", titleEn: "Menus", scope: "PLATFORM", permission: "menu:read", gate: "PLATFORM_ADMIN" }, operations: ["menu:read", "menu:create"] },
  { page: { key: "print", path: "/print", titleZh: "打印", titleEn: "Print", scope: "TENANT", permission: null, gate: "PERMISSION" }, operations: ["print:submit"] }
];
describe("功能树筛选", () => {
  it("权限命中只保留操作和祖先，且不修改快照", () => {
    const tree = buildFeatureTree(entries, "menu:create", "", true);
    expect(tree.map(row => row.id)).toEqual(["PLATFORM"]);
    expect(tree[0].children?.[0].children?.map(row => row.permission)).toEqual(["menu:create"]);
    expect(entries[0].operations).toHaveLength(2);
  });
  it("管理域过滤与英文展示保持页面无基础权限的真实状态", () => {
    const tree = buildFeatureTree(entries, "", "TENANT", false);
    expect(tree[0].children?.[0]).toMatchObject({ title: "Print", permission: null });
    expect(buildFeatureTree(entries, "missing", "", true)).toEqual([]);
  });
});
