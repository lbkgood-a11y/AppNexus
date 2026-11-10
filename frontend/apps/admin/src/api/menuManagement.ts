import {http} from "@/utils/http";
import type {MenuInput,MenuManagementApi,MenuNode,NavigationPage} from "@appnexus/configuration";
/** 菜单写操作逐次获取 CSRF；version 是并发控制值，不是权限凭据。 */
async function write<T>(method:"post"|"put"|"delete",path:string,data?:object,params?:object){const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");const result=await http.request<T>(method,"/bff/platform/menus"+path,{data,params,headers:{[csrf.headerName]:csrf.token}});try{await(await import("@/router/navigation")).refreshNavigation();}catch{/* 写入已成功；导航暂时不可用由下一次路由或焦点复核重试，不能误报写入失败。 */}return result;}
/** 平台菜单 API 适配器；操作者身份只来自服务端会话。 */
export const menuManagementApi:MenuManagementApi={
 capabilities:()=>http.request<string[]>("get","/bff/platform/menus/capabilities"),
 pages:()=>http.request<NavigationPage[]>("get","/bff/platform/menus/pages"),
 list:()=>http.request<MenuNode[]>("get","/bff/platform/menus"),
 create:input=>write<MenuNode>("post","",input),
 update:(id,version,input)=>write<MenuNode>("put","/"+encodeURIComponent(id),{version,...input}),
 remove:(id,version)=>write<void>("delete","/"+encodeURIComponent(id),undefined,{version})
};
