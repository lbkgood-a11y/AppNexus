import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { nextTick, type DirectiveBinding, type ObjectDirective, type VNode } from "vue";

const state = vi.hoisted(() => ({ request: vi.fn(), tenant: { value: undefined as { tenantId: string } | undefined } }));
vi.mock("@/utils/http", () => ({ http: { request: state.request } }));
vi.mock("@/utils/tenantContext", () => ({ currentTenant: state.tenant }));
import { operation } from "./index";

let release: (() => void) | undefined;
/** 无浏览器凭据或真实 API 的指令生命周期验证，重点检查默认拒绝和撤权后响应。 */
beforeEach(() => {
  vi.useFakeTimers(); state.request.mockReset(); state.tenant.value = undefined;
  vi.stubGlobal("window", new EventTarget()); vi.stubGlobal("location", { hash: "#/platform/users", pathname: "/" });
});
afterEach(() => { release?.(); release = undefined; vi.useRealTimers(); vi.unstubAllGlobals(); });
async function settle() { await Promise.resolve(); await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick(); }
function mount(code: string) {
  const element = { style: { display: "" } } as HTMLElement;
  const directive = operation as ObjectDirective<HTMLElement, string>;
  const binding = { value: code } as DirectiveBinding<string>;
  const node = {} as VNode<unknown, HTMLElement>;
  directive.beforeMount?.(element, binding, node, null);
  directive.mounted?.(element, binding, node, null);
  release = () => directive.unmounted?.(element, binding, node, null);
  return element;
}
it("hides before capability proof and removes access after authority changes", async () => {
  state.request.mockResolvedValue(["identity.account:revoke_sessions"]);
  const element = mount("identity.account:revoke_sessions"); expect(element.style.display).toBe("none");
  await settle(); expect(element.style.display).toBe("");
  state.request.mockResolvedValue([]); window.dispatchEvent(new Event("appnexus:authority-changed"));
  await settle(); expect(element.style.display).toBe("none");
});
it("does not restore all buttons when capability read fails", async () => {
  state.request.mockRejectedValue(new Error("unavailable")); const element = mount("identity.account:create");
  await settle(); expect(element.style.display).toBe("none");
});
it("uses actual tenant role update permission for status and retains alternatives", async () => {
  vi.stubGlobal("location", { hash: "#/access/roles", pathname: "/" }); state.tenant.value = { tenantId: "tenant-a" };
  state.request.mockResolvedValue(["access.tenant.role:update"]); const element = mount("@role:enable");
  await settle(); expect(element.style.display).toBe("");
  expect(state.request).toHaveBeenCalledWith("get", "/api/operation-capabilities");
});
it("rejects a previous account's late capability response after logout", async () => {
  let resolve!: (codes: string[]) => void;
  state.request.mockImplementation(() => new Promise<string[]>(done => { resolve = done; }));
  const element = mount("identity.account:create");
  window.dispatchEvent(new Event("appnexus:session-ended"));
  resolve(["identity.account:create"]); await settle();
  expect(element.style.display).toBe("none");
});
