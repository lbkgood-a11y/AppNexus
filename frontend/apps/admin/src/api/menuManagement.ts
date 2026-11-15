import {operationGroups} from "./operationGroups";
import {http} from "@/utils/http";
import type {MenuInput,MenuManagementApi,MenuNode,NavigationPage} from "@appnexus/configuration";
/** 菜单写操作逐次获取 CSRF；version 是并发控制值，不是权限凭据。 */
async function write<T>(method:"post"|"put"|"delete",path:string,data?:object,params?:object){const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");const result=await http.request<T>(method,"/bff/platform/menus"+path,{data,params,headers:{[csrf.headerName]:csrf.token}});try{await(await import("@/router/navigation")).refreshNavigation();}catch{/* 写入已成功；导航暂时不可用由下一次路由或焦点复核重试，不能误报写入失败。 */}return result;}
/** 平台菜单 API 适配器；操作者身份只来自服务端会话。 */
export const menuManagementApi:MenuManagementApi={
 capabilities:()=>operationGroups("platform",{read:"NavigationMenu.list",create:"NavigationMenu.create",update:"NavigationMenu.update",delete:"NavigationMenu.delete"}),
 pages:()=>http.request<NavigationPage[]>("get","/bff/platform/menus/pages"),
 list:()=>http.request<MenuNode[]>("get","/bff/platform/menus"),
 create:input=>write<MenuNode>("post","",input),
 update:(id,version,input)=>write<MenuNode>("put","/"+encodeURIComponent(id),{version,...input}),
 remove:(id,version)=>write<void>("delete","/"+encodeURIComponent(id),undefined,{version})
};

/** 读取代码功能目录；只使用当前会话，后端复核平台身份和菜单读取权限。 */
export function getFeatureCatalog() {
 return http.request<import("@appnexus/configuration").FeatureRegistration[]>("get","/bff/platform/menus/features");
}

/** 读取真实接口绑定的操作目录；不通过权限码推断操作名称。 */
export function getOperationCatalog(){return http.request<import("@appnexus/configuration").OperationDefinition[]>("get","/bff/platform/operation-catalog");}

/** 全对象目录仅提供代码元数据，读取仍由服务端复核平台管理权。 */
export function getObjectCatalog(){return http.request<import("@appnexus/configuration").ObjectDefinition[]>("get","/bff/platform/object-catalog");}
