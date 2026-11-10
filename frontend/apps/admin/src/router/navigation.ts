import type { RouteRecordRaw } from "vue-router";
import { getNavigation } from "@/api/routes";
import { addPathMatch } from "./utils";
import { router } from "@/router";
import { usePermissionStoreHook } from "@/store/modules/permission";
import { useMultiTagsStoreHook } from "@/store/modules/multiTags";
import { currentTenant } from "@/utils/tenantContext";
import { buildNavigationMenus, safeNavigationUrl } from "./navigationModel";

/** 显式构建内注册表；不接受路径模糊匹配、远程组件或服务端脚本。 */
const pages = {
  "workflow/designer": {path:"/workflow/designer",name:"WorkflowDesigner",component:()=>import("@/views/workflow/designer.vue")},
  "workflow/index": {path:"/workflow/center",name:"WorkflowCenter",component:()=>import("@/views/workflow/index.vue")},
  "platform/tenants": {path:"/platform/tenants",name:"PlatformTenantManagement",component:()=>import("@/views/platform/tenants.vue")},
  "platform/users": {path:"/platform/users",name:"PlatformUserManagement",component:()=>import("@/views/platform/users.vue")},
  "platform/menus": {path:"/platform/menus",name:"PlatformMenuManagement",component:()=>import("@/views/platform/menus.vue")},
  "platform/roles": {path:"/platform/roles",name:"PlatformRoleManagement",component:()=>import("@/views/platform/roles.vue")},
  "platform/authorization": {path:"/platform/authorization",name:"PlatformAuthorizationCenter",component:()=>import("@/views/platform/authorization.vue")},
  "organization/index": {path:"/organization/manage",name:"OrganizationManagement",component:()=>import("@/views/organization/index.vue")},
  "roles/index": {path:"/access/roles",name:"TenantRoleManagement",component:()=>import("@/views/roles/index.vue")},
  "roles/authorization": {path:"/access/authorization",name:"TenantAuthorizationCenter",component:()=>import("@/views/roles/authorization.vue")},
  "configuration/dictionaries": {path:"/configuration/dictionaries",name:"DictionaryManagement",component:()=>import("@/views/configuration/dictionaries.vue")}
};
let generation=0;
let pending:Promise<void>|undefined;
let signature="";
let contextKey="";
let removers:Array<()=>void>=[];
let allowedPaths=new Set<string>();

/** 清除本人导航状态，并让旧请求失效；会话结束和租户切换时调用。 */
export function resetNavigation(){generation++;pending=undefined;signature="";contextKey="";removers.forEach(remove=>remove());removers=[];allowedPaths.clear();usePermissionStoreHook().navigationReady=false;}

/** 已注册业务路由即使被移除，直接输入地址也返回无权限，而不是落到另一页面。 */
export function isBusinessPath(path:string){return Object.values(pages).some(page=>page.path===path);}
export function canVisitBusinessPath(path:string){return allowedPaths.has(path);}

/**
 * 从服务端刷新导航并原子替换路由、菜单与失效标签；网络失败保留上次结果并向调用方报错。
 * @param force 默认 false，首次加载后复用内存；true 每次重新获取权威快照
 */
export function loadNavigation(force=false):Promise<void> {
  const key=JSON.stringify(currentTenant.value??null);
  if(contextKey&&contextKey!==key)resetNavigation();
  contextKey=key;
  if(pending)return pending;
  if(!force&&usePermissionStoreHook().navigationReady)return Promise.resolve();
  const epoch=generation;
  const task=(async()=>{
    const snapshot=await getNavigation();
    if(epoch!==generation)return;
    const context=currentTenant.value;
    if((snapshot.tenantId??null)!==(context?.tenantId??null)||snapshot.contextVersion!==(context?.version??0)) {
      throw new Error("Navigation context changed");
    }
    const nextSignature=JSON.stringify(snapshot);
    if(signature===nextSignature){usePermissionStoreHook().navigationReady=true;return;}
    const paths=new Set<string>();
    const routes:RouteRecordRaw[]=[];
    for(const item of snapshot.items){
      if(item.type==="EXTERNAL"&&!safeNavigationUrl(item.externalUrl))throw new Error("Invalid external navigation address");
      if(item.type!=="MENU")continue;
      const page=pages[item.component as keyof typeof pages];
      if(!page||page.path!==item.path||paths.has(page.path))throw new Error("Unregistered or duplicate navigation page");
      paths.add(page.path);
      routes.push({...page,meta:{title:{"zh-CN":item.titleZh,zh:item.titleZh,en:item.titleEn},icon:item.icon,showLink:item.visible,navigation:true}});
    }
    const menus=buildNavigationMenus(snapshot.items);
    const bindNames=(items:typeof menus)=>items.forEach(item=>{
      const page=Object.values(pages).find(page=>page.path===item.path);
      if(page)item.name=page.name;
      if(item.children)bindNames(item.children);
    });
    bindNames(menus);
    removers.forEach(remove=>remove());removers=[];
    for(const route of routes)removers.push(router.addRoute("Home",route));
    /** 目录自身只做跳转；外链目录不自动跳转外站，继续由模板链接显式打开。 */
    const addDirectories=(items:typeof menus)=>items.forEach(item=>{
      if(item.children?.length){
        const first=item.children.find(child=>!safeNavigationUrl(child.path));
        if(first)removers.push(router.addRoute("Home",{path:item.path,name:item.name,redirect:first.path}));
        addDirectories(item.children);
      }
    });
    addDirectories(menus);
    allowedPaths=paths;
    const store=usePermissionStoreHook();
    store.handleWholeMenus(menus);
    store.navigationReady=true;
    store.cachePageList=[];
    const tags=useMultiTagsStoreHook();
    tags.handleTags("equal",tags.multiTags.filter(tag=>!tag.path||(!isBusinessPath(tag.path)&&!tag.path.startsWith("/navigation/"))||paths.has(tag.path)).map(tag=>{const current=routes.find(route=>route.path===tag.path);return current?{...tag,meta:current.meta}:tag;}));
    signature=nextSignature;
    addPathMatch();
  })();
  pending=task;
  void task.finally(()=>{if(pending===task)pending=undefined;}).catch(()=>{});
  return task;
}

/** 焦点及活跃会话复核后同步权限变更，失效页面离开；后端始终独立逐请求鉴权。 */
export async function refreshNavigation(){
  await loadNavigation(true);
  const path=router.currentRoute.value.path;
  if(isBusinessPath(path)&&!canVisitBusinessPath(path))await router.replace("/access-denied");
}
