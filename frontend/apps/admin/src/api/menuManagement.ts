import {http} from "@/utils/http";
import type {MenuInput,MenuManagementApi,MenuNode} from "@appnexus/configuration";
/** 菜单写操作逐次获取 CSRF；version 是并发控制值，不是权限凭据。 */
async function write<T>(method:"post"|"put"|"delete",path:string,data?:object,params?:object){const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request<T>(method,"/bff/platform/menus"+path,{data,params,headers:{[csrf.headerName]:csrf.token}});}
/** 平台菜单 API 适配器；操作者身份只来自服务端会话。 */
export const menuManagementApi:MenuManagementApi={
 list:()=>http.request<MenuNode[]>("get","/bff/platform/menus"),
 create:input=>write<MenuNode>("post","",input),
 update:(id,version,input)=>write<MenuNode>("put","/"+encodeURIComponent(id),{version,...input}),
 remove:(id,version)=>write<void>("delete","/"+encodeURIComponent(id),undefined,{version})
};
