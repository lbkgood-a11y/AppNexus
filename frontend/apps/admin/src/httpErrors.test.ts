import Axios, { AxiosError, CanceledError } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
const transport = vi.hoisted(() => ({ request: vi.fn(), finish: vi.fn(), clearSession: vi.fn() }));
// 请求模块在导入时注册浏览器事件；本组仅验证协议，使用最小事件宿主。
vi.hoisted(() => { vi.stubGlobal("window", new EventTarget()); });
vi.mock("axios", async importOriginal => {
  const original = await importOriginal<typeof import("axios")>();
  return { ...original, default: { ...original.default, create: () => ({ request: transport.request }) } };
});
vi.mock("@/plugins/i18n", () => ({ i18n: { global: { t: (key: string) => key, te: () => false } } }));
vi.mock("@/utils/tenantContext", async () => {
  const { shallowRef } = await import("vue");
  return { currentTenant: shallowRef(), beginTenantWrite: () => transport.finish, reloadTenant: vi.fn() };
});
vi.mock("@/utils/sessionLifecycle", () => ({ clearSession: transport.clearSession }));
import { currentTenant, reloadTenant } from "@/utils/tenantContext";
import { http, requestErrorMessage } from "@/utils/http";

/** 构造协议错误，验证客户端边界；不代表真实服务端已返回该错误。 */
function failure(status?: number, code?: string): AxiosError {
  return new AxiosError("internal diagnostic", undefined, undefined, undefined,
    status ? { status, data: { code }, statusText: "", headers: {}, config: { headers: {} } } as any : undefined);
}
describe("请求异常协议边界", () => {
  it("租户恢复成功后静默取消旧写入且不重放", async () => {
    vi.mocked(reloadTenant).mockResolvedValueOnce();
    transport.request.mockRejectedValueOnce(failure(409, "TENANT_CONTEXT_CHANGED"));
    await expect(http.request("post", "/api/acceptance")).rejects.toSatisfy(Axios.isCancel);
    expect(reloadTenant).toHaveBeenCalledTimes(1);
    expect(transport.request).toHaveBeenCalledTimes(1);
    expect(transport.finish).toHaveBeenCalledTimes(1);
  });
  it("租户恢复失败向调用方传播且不重放请求", async () => {
    const unavailable = failure(503);
    vi.mocked(reloadTenant).mockRejectedValueOnce(unavailable);
    transport.request.mockRejectedValueOnce(failure(409, "TENANT_CONTEXT_CHANGED"));
    await expect(http.request("get", "/api/acceptance")).rejects.toBe(unavailable);
    expect(transport.request).toHaveBeenCalledTimes(1);
  });
  afterEach(() => { currentTenant.value = undefined; vi.clearAllMocks(); });
  it.each([[403, "account.forbidden"], [429, "account.rateLimited"], [503, "account.unavailable"]])("HTTP %s 映射受控文案", (status, key) => {
    expect(requestErrorMessage(failure(status as number))).toBe(key);
  });
  it("取消静默，401交由会话，MFA保留专用引导", () => {
    expect(requestErrorMessage(new CanceledError())).toBe("");
    expect(requestErrorMessage(failure(401))).toBe("");
    expect(requestErrorMessage(failure(403, "MFA_STEP_UP_REQUIRED"))).toBe("account.errors.MFA_STEP_UP_REQUIRED");
  });
  it("网络或写入超时不自动重放，写入计数始终释放", async () => {
    transport.request.mockRejectedValueOnce(new AxiosError("timeout", "ECONNABORTED"));
    await expect(http.request("post", "/api/acceptance")).rejects.toThrow("timeout");
    expect(transport.request).toHaveBeenCalledTimes(1);
    expect(transport.finish).toHaveBeenCalledTimes(1);
    expect(transport.clearSession).not.toHaveBeenCalled();
    expect(requestErrorMessage(failure())).toBe("account.unavailable");
  });
  it.each([true, false])("租户切换后在途请求静默：成功响应=%s", async success => {
    currentTenant.value = { tenantId: "a", memberId: "m", name: "A", version: 1 };
    let settle!: (value: any) => void;
    transport.request.mockImplementationOnce(() => new Promise((resolve, reject) => { settle = success ? resolve : reject; }));
    const pending = http.request("get", "/api/acceptance");
    currentTenant.value = { tenantId: "b", memberId: "n", name: "B", version: 2 };
    settle(success ? { data: [] } : failure(409, "ROLE_CHANGED"));
    await expect(pending).rejects.toSatisfy(Axios.isCancel);
  });
  it("不同租户版本相同时仍丢弃旧响应", async () => {
    currentTenant.value = { tenantId: "a", memberId: "m", name: "A", version: 1 };
    transport.request.mockImplementationOnce(() => {
      currentTenant.value = { tenantId: "b", memberId: "n", name: "B", version: 1 };
      return Promise.resolve({ data: ["old"] });
    });
    await expect(http.request("get", "/api/acceptance")).rejects.toSatisfy(Axios.isCancel);
  });
  it("401即使来自旧租户仍执行会话清理且只发一次请求", async () => {
    currentTenant.value = { tenantId: "a", memberId: "m", name: "A", version: 1 };
    transport.request.mockImplementationOnce(() => {
      currentTenant.value = { tenantId: "b", memberId: "n", name: "B", version: 2 };
      return Promise.reject(failure(401));
    });
    await expect(http.request("get", "/api/acceptance")).rejects.toBeInstanceOf(AxiosError);
    expect(transport.clearSession).toHaveBeenCalledWith("expired");
    expect(transport.request).toHaveBeenCalledTimes(1);
  });
});
