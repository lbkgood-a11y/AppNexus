import type { RouteRecordRaw } from "vue-router";
import { getNavigation } from "@/api/routes";
import { addPathMatch } from "./utils";
import { router } from "@/router";
import { usePermissionStoreHook } from "@/store/modules/permission";
import { useMultiTagsStoreHook } from "@/store/modules/multiTags";
import { currentTenant } from "@/utils/tenantContext";
import { buildNavigationMenus, navigationIcon, safeNavigationUrl } from "./navigationModel";

/** 显式构建内注册表；不接受路径模糊匹配、远程组件或服务端脚本。 */
const pages = {
  "organization/personnel": {path:"/organization/personnel",name:"OrganizationPersonnel",component:()=>import("@/views/organization/personnel.vue")},
  "master-data/partner": {path:"/master-data/partner",name:"MasterDataBusinessPartner",component:()=>import("@/views/master-data/partner.vue")},
  "master-data/storage": {path:"/master-data/storage",name:"MasterDataStorage",component:()=>import("@/views/master-data/storage.vue")},
  "warehousing/inbound-notices": {path:"/warehousing/inbound-notices",name:"WarehousingInboundNotices",component:()=>import("@/views/warehousing/documents.vue")},
  "warehousing/inbound-tasks": {path:"/warehousing/inbound-tasks",name:"WarehousingInboundTasks",
    component:()=>import("@/views/warehousing/inbound-tasks.vue")},
  "warehousing/inbound-workbench": {path:"/warehousing/inbound-workbench",name:"WarehousingInboundWorkbench",
    component:()=>import("@/views/warehousing/inbound-workbench.vue")},
  "warehousing/inbound-quality-orders": { path: "/warehousing/inbound-quality-orders", name: "WarehousingInboundQualityOrders",
    component: () => import("@/views/warehousing/inbound-quality-orders.vue") },
  "warehousing/inbound-metering-orders": { path: "/warehousing/inbound-metering-orders", name: "WarehousingInboundMeteringOrders",
    component: () => import("@/views/warehousing/inbound-metering-orders.vue") },
  "warehousing/inbound-receipt-orders": { path: "/warehousing/inbound-receipt-orders", name: "WarehousingInboundReceiptOrders",
    component: () => import("@/views/warehousing/inbound-receipt-orders.vue") },
  "warehousing/inventory-overview": {path:"/warehousing/inventory-overview",name:"WarehousingInventoryOverview",component:()=>import("@/views/warehousing/inventory-overview.vue")},
  "warehousing/personnel-authorization": { path: "/warehousing/personnel-authorization", name: "WarehousePersonnelAuthorization",
    component: () => import("@/views/warehousing/personnel-authorization.vue") },
  "master-data/warehouse": {path:"/master-data/warehouse",name:"MasterDataWarehouse",component:()=>import("@/views/master-data/warehouse.vue")},
  "master-data/country-region": {path:"/master-data/country-region",name:"MasterDataCountryRegion",component:()=>import("@/views/master-data/country-region.vue")},
  "master-data/subdivision": {path:"/master-data/subdivision",name:"MasterDataSubdivision",component:()=>import("@/views/master-data/subdivision.vue")},
  "master-data/product-sku": {path:"/master-data/product-sku",name:"MasterDataProductSku",component:()=>import("@/views/master-data/product-sku.vue")},
  "master-data/product": {path:"/master-data/product",name:"MasterDataProduct",component:()=>import("@/views/master-data/product.vue")},
  "master-data/product-category": {path:"/master-data/product-category",name:"MasterDataProductCategory",component:()=>import("@/views/master-data/product-category.vue")},
  "master-data/product-unit": {path:"/master-data/product-unit",name:"MasterDataProductUnit",component:()=>import("@/views/master-data/product-unit.vue")},
  "master-data/product-attribute": {path:"/master-data/product-attribute",name:"MasterDataProductAttribute",component:()=>import("@/views/master-data/product-attribute.vue")},
  "master-data/product-template": {path:"/master-data/product-template",name:"MasterDataProductTemplate",component:()=>import("@/views/master-data/product-template.vue")},
  "warehousing/inbound-templates": {path:"/warehousing/inbound-templates",name:"WarehousingInboundTemplates",
    component:()=>import("@/views/warehousing/inbound-templates.vue")},

  "platform/business-objects": {path:"/platform/business-objects",name:"PlatformBusinessObjectManagement",component:()=>import("@/views/platform/business-objects.vue")},
  "configuration/coding-rules": {path:"/configuration/coding-rules",name:"CodingRules",component:()=>import("@/views/configuration/coding-rules.vue")},
  "business-objects/index": {path:"/business-objects/workbench",name:"DocumentWorkbench",component:()=>import("@/views/business-objects/index.vue")},
  "workflow/leave": {path:"/workflow/leave",name:"LeaveRequests",component:()=>import("@/views/workflow/leave.vue")},
  "print/index": {path:"/print/management",name:"PrintManagement",component:()=>import("@/views/print/index.vue")},
  "workflow/designer": {path:"/workflow/designer",name:"WorkflowDesigner",component:()=>import("@/views/workflow/designer.vue")},
  "workflow/index": {path:"/workflow/center",name:"WorkflowCenter",component:()=>import("@/views/workflow/index.vue")},
  "platform/tenants": {path:"/platform/tenants",name:"PlatformTenantManagement",component:()=>import("@/views/platform/tenants.vue")},
  "platform/users": {path:"/platform/users",name:"PlatformUserManagement",component:()=>import("@/views/platform/users.vue")},
  "platform/features": {path:"/platform/features",name:"PlatformFeatureCatalog",component:()=>import("@/views/platform/features.vue")},
  "configuration/attachment-policies": {path:"/configuration/attachment-policies",name:"AttachmentPolicies",component:()=>import("@/views/configuration/attachment-policies.vue")},
  "platform/events": {path:"/platform/events",name:"EventWorkbench",component:()=>import("@/views/platform/events.vue")},
  "configuration/attachments": {path:"/configuration/attachments",name:"AttachmentLedger",component:()=>import("@/views/configuration/attachments.vue")},
  "platform/menus": {path:"/platform/menus",name:"PlatformMenuManagement",component:()=>import("@/views/platform/menus.vue")},
  "platform/roles": {path:"/platform/roles",name:"PlatformRoleManagement",component:()=>import("@/views/platform/roles.vue")},
  "platform/authorization": {path:"/platform/authorization",name:"PlatformAuthorizationCenter",component:()=>import("@/views/platform/authorization.vue")},
  "organization/index": {path:"/organization/manage",name:"OrganizationManagement",component:()=>import("@/views/organization/index.vue")},
  "roles/index": {path:"/access/roles",name:"TenantRoleManagement",component:()=>import("@/views/roles/index.vue")},
  "roles/authorization": {path:"/access/authorization",name:"TenantAuthorizationCenter",component:()=>import("@/views/roles/authorization.vue")},
  "configuration/parameters": {path:"/configuration/parameters",name:"TenantParameters",component:()=>import("@/views/configuration/parameters.vue")},
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
      // 仓储页面的查询参数可定位单据，整个业务工作区只占一个平台标签。
      const warehouseWorkspace = page.path.startsWith("/warehousing/");
      routes.push({...page,meta:{title:{"zh-CN":item.titleZh,zh:item.titleZh,en:item.titleEn},icon:navigationIcon(item),showLink:item.visible,navigation:true,
        ...(warehouseWorkspace ? {dynamicLevel:1,reuseOnQueryChange:true} : {})}});
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
    const registered = new Map(routes.map(route => [route.path, route.meta]));
    const restoredWorkspacePaths = new Set<string>();
    const restoredTags = tags.multiTags
      .filter(tag => !tag.path || (!isBusinessPath(tag.path) && !tag.path.startsWith("/navigation/")) || paths.has(tag.path))
      .filter(tag => {
        if (registered.get(tag.path)?.dynamicLevel !== 1) return true;
        if (restoredWorkspacePaths.has(tag.path)) return false;
        restoredWorkspacePaths.add(tag.path);
        return true;
      })
      .map(tag => registered.has(tag.path) ? { ...tag, meta: registered.get(tag.path) } : tag);
    tags.handleTags("equal", restoredTags);
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
