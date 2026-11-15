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
