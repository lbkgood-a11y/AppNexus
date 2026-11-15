import {beforeEach,expect,it,vi} from "vitest";
const state=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("@/utils/http",()=>({http:{request:state.request}}));
import {workflowApi} from "./workflow";
/** 页面分组依赖真实操作身份，不把任务对象准入误当成流程管理权。 */
beforeEach(()=>state.request.mockReset());
it("keeps task ownership separate from workflow administration",async()=>{
 state.request.mockResolvedValue(["WorkflowTask.complete","WorkflowTask.read","WorkflowDefinition.save","WorkflowInstance.start"]);
 expect(await workflowApi.capabilities()).toEqual(["edit","start"]);
 expect(state.request).toHaveBeenCalledExactlyOnceWith("get","/api/operation-decisions");
});
