import { afterEach, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({ probe: vi.fn(), replace: vi.fn(), load: vi.fn() }));
vi.mock("@/api/tenant", () => ({ probeCurrentTenant: state.probe }));
vi.mock("@pureadmin/utils", () => ({ storageLocal: () => ({ removeItem: vi.fn() }) }));
vi.mock("@/config", () => ({ responsiveStorageNameSpace: () => "test" }));
vi.mock("@/router", () => ({ router: { currentRoute: { value: { path: "/welcome", query: {} } }, replace: state.replace } }));
vi.mock("@/router/navigation", () => ({ resetNavigation: vi.fn(), loadNavigation: state.load }));
vi.mock("@/store/modules/multiTags", () => ({ useMultiTagsStoreHook: () => ({ handleTags: vi.fn() }) }));
vi.mock("@/store/modules/permission", () => ({ usePermissionStoreHook: () => ({ cachePageList: [] }) }));
vi.mock("@/layout/types", () => ({ routerArrays: [] }));
import { currentTenant, reloadTenant } from "./tenantContext";

afterEach(() => { currentTenant.value = undefined; vi.resetAllMocks(); });

/** 并发冲突只探测并重建一次；版本与成员均使用服务端探测结果。 */
it("coalesces concurrent tenant recovery and refreshes the workspace once", async () => {
  let resolve!: (value: unknown) => void;
  state.probe.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
  const first = reloadTenant(); const second = reloadTenant();
  expect(first).toBe(second);
  await vi.waitFor(() => expect(state.probe).toHaveBeenCalledTimes(1));
  const tenant = { tenantId: "b", memberId: "m", name: "B", version: 2 };
  resolve(tenant); await Promise.all([first, second]);
  expect(currentTenant.value).toEqual(tenant);
  expect(state.replace).toHaveBeenCalledTimes(1);
  expect(state.load).toHaveBeenCalledTimes(1);
});

/** 故障不吞掉错误，失败结束后下一次显式恢复仍能重试。 */
it("releases failed recovery so the next recovery can succeed", async () => {
  state.probe.mockRejectedValueOnce(new Error("offline"));
  await expect(reloadTenant()).rejects.toThrow("offline");
  state.probe.mockResolvedValueOnce(undefined);
  await reloadTenant();
  expect(state.probe).toHaveBeenCalledTimes(2);
  expect(state.replace).toHaveBeenCalledTimes(1);
});
