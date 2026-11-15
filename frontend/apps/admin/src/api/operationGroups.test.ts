import {expect,it,vi} from "vitest";
const state=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("@/utils/http",()=>({http:{request:state.request}}));
import {operationGroups} from "./operationGroups";
/** 管理域和未知操作默认拒绝投影，分组不构成业务执行授权。 */
it("uses the selected domain and omits unregistered capabilities",async()=>{
 state.request.mockResolvedValue(["NavigationMenu.create"]);
 expect(await operationGroups("platform",{create:"NavigationMenu.create",remove:"NavigationMenu.delete"})).toEqual(["create"]);
 expect(state.request).toHaveBeenCalledWith("get","/bff/operation-decisions");
});
it("keeps tenant capability groups isolated from platform operation identities",async()=>{
 state.request.mockResolvedValue(["OrganizationUnit.create","NavigationMenu.create"]);
 expect(await operationGroups("tenant",{CREATE:"OrganizationUnit.create",DELETE:"OrganizationUnit.delete"})).toEqual(["CREATE"]);
 expect(state.request).toHaveBeenLastCalledWith("get","/api/operation-decisions");
});
