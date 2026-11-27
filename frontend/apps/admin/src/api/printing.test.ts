import { beforeEach, expect, it, vi } from "vitest";
const state=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("@/utils/http",()=>({http:{request:state.request}}));
import {printingApi} from "./printing";
/** 打印页面能力只能来自统一操作身份，读取失败不得回退旧权限或全部能力。 */
beforeEach(()=>state.request.mockReset());
it("maps registered operations to page groups without using the legacy endpoint",async()=>{
 state.request.mockResolvedValue(["PrintTemplate.update","PrintJob.list","Unknown.operation"]);
 expect(await printingApi.capabilities()).toEqual(["design","jobs"]);
 expect(state.request).toHaveBeenCalledExactlyOnceWith("get","/api/operation-decisions");
});

it("saves a template with a UUID idempotency key when randomUUID is unavailable", async () => {
  vi.stubGlobal("crypto", { getRandomValues: crypto.getRandomValues.bind(crypto) });
  try {
    const draft = { code: "print_test", name: "测试模板", category: "A4", dataContract: "test.v1",
      scope: {}, template: { panels: [] }, revision: -1 };
    state.request.mockResolvedValueOnce({ headerName: "X-CSRF-TOKEN", token: "test" });
    state.request.mockResolvedValueOnce({ id: "saved" });

    await expect(printingApi.saveTemplate(undefined, draft)).resolves.toEqual({ id: "saved" });
    expect(state.request).toHaveBeenNthCalledWith(2, "post", "/api/printing/templates", {
      data: draft,
      headers: {
        "X-CSRF-TOKEN": "test",
        "Idempotency-Key": expect.stringMatching(/^[a-f\d]{8}-[a-f\d]{4}-4[a-f\d]{3}-[89ab][a-f\d]{3}-[a-f\d]{12}$/)
      }
    });
  } finally {
    vi.unstubAllGlobals();
  }
});
