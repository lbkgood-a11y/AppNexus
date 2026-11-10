import {beforeEach,describe,expect,it,vi} from "vitest";
import type {MenuNode} from "@appnexus/configuration";

const state=vi.hoisted(()=>({
  get:vi.fn(),remove:vi.fn(),add:vi.fn(),tags:[] as any[],store:{navigationReady:false,cachePageList:[] as any[],handleWholeMenus:vi.fn()},
  tenant:{value:undefined as any},path:"/welcome"
}));
vi.mock("@/api/routes",()=>({getNavigation:state.get}));
vi.mock("@/router",()=>({router:{addRoute:state.add,currentRoute:{value:{path:state.path}},replace:vi.fn()}}));
vi.mock("./utils",()=>({addPathMatch:vi.fn()}));
vi.mock("@/utils/tenantContext",()=>({currentTenant:state.tenant}));
vi.mock("@/store/modules/permission",()=>({usePermissionStoreHook:()=>state.store}));
vi.mock("@/store/modules/multiTags",()=>({useMultiTagsStoreHook:()=>({multiTags:state.tags,handleTags:(_mode:string,tags:any[])=>{state.tags=tags;}})}));
import {loadNavigation,resetNavigation,canVisitBusinessPath} from "./navigation";

/** 路由生命周期验证：覆盖撤权清理和异步上下文竞争，不以菜单截图代替状态验证。 */
describe("navigation lifecycle",()=>{
  const page:MenuNode={id:"organization",type:"MENU",code:"org",titleZh:"组织",titleEn:"Organizations",path:"/organization/manage",
    component:"organization/index",scope:"TENANT",sortOrder:1,visible:false,enabled:true,version:0};
  beforeEach(()=>{resetNavigation();vi.clearAllMocks();state.tenant.value=undefined;state.tags=[];state.add.mockReturnValue(state.remove);});
  it("registers hidden pages and removes revoked routes, tags and cache",async()=>{
    state.get.mockResolvedValueOnce({tenantId:null,contextVersion:0,items:[page]});
    await loadNavigation();
    expect(canVisitBusinessPath(page.path!)).toBe(true);
    expect(state.add).toHaveBeenCalledWith("Home",expect.objectContaining({name:"OrganizationManagement",meta:expect.objectContaining({showLink:false})}));
    state.tags=[{path:page.path},{path:"/account/profile"}];state.store.cachePageList=["OrganizationManagement"];
    state.get.mockResolvedValueOnce({tenantId:null,contextVersion:0,items:[]});
    await loadNavigation(true);
    expect(state.remove).toHaveBeenCalled();
    expect(canVisitBusinessPath(page.path!)).toBe(false);
    expect(state.tags).toEqual([{path:"/account/profile"}]);
    expect(state.store.cachePageList).toEqual([]);
  });
  it("discards a late response after context reset",async()=>{
    let resolve!:(value:unknown)=>void;
    state.get.mockReturnValueOnce(new Promise(done=>{resolve=done;}));
    const old=loadNavigation();resetNavigation();
    resolve({tenantId:null,contextVersion:0,items:[page]});await old;
    expect(state.add).not.toHaveBeenCalled();
    expect(state.store.navigationReady).toBe(false);
  });
  it("does not install an unknown component or mismatched context",async()=>{
    state.get.mockResolvedValueOnce({tenantId:null,contextVersion:0,items:[{...page,component:"remote/script"}]});
    await expect(loadNavigation()).rejects.toThrow("Unregistered");
    expect(state.add).not.toHaveBeenCalled();
    state.get.mockResolvedValueOnce({tenantId:"other",contextVersion:1,items:[]});
    await expect(loadNavigation()).rejects.toThrow("context");
  });
  it("keeps task workspace and designer as independent authorized routes",async()=>{
    const task={...page,id:"workflow",component:"workflow/index",path:"/workflow/center"};
    const designer={...page,id:"designer",component:"workflow/designer",path:"/workflow/designer",permission:"workflow:read"};
    state.get.mockResolvedValueOnce({tenantId:null,contextVersion:0,items:[task,designer]});await loadNavigation();
    expect(canVisitBusinessPath(task.path)).toBe(true);expect(canVisitBusinessPath(designer.path)).toBe(true);
    state.get.mockResolvedValueOnce({tenantId:null,contextVersion:0,items:[task]});await loadNavigation(true);
    expect(canVisitBusinessPath(task.path)).toBe(true);expect(canVisitBusinessPath(designer.path)).toBe(false);
  });
});
