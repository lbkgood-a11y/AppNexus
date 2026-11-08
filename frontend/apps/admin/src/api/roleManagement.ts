import {http} from "@/utils/http";
import type {RoleCreate,RoleItem,RoleManagementApi,RolePage,RoleQuery,RoleStatus,RoleUpdate} from "@appnexus/authorization";
/** 每次角色写操作重新取得 CSRF；base 由可信页面装配，不接受表单输入。 */
function api(base:string):RoleManagementApi{async function write<T>(method:"post"|"put"|"patch",path:string,data:object){const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request<T>(method,base+path,{data,headers:{[csrf.headerName]:csrf.token}});}return{list:(query:RoleQuery)=>http.request<RolePage>("get",base,{params:query}),create:(input:RoleCreate)=>write<RoleItem>("post","",input),update:(id:string,input:RoleUpdate)=>write<RoleItem>("put","/"+encodeURIComponent(id),input),changeStatus:(id:string,status:RoleStatus,reason:string,version:number)=>write<RoleItem>("patch","/"+encodeURIComponent(id)+"/status",{status,reason,version}),remove:(id:string,confirmationCode:string,reason:string,version:number)=>write<void>("post","/"+encodeURIComponent(id)+"/delete",{confirmationCode,reason,version})};}
export const platformRoleApi=api("/bff/platform/roles");
export const tenantRoleApi=api("/api/access/roles");
/** 平台代管入口只由租户详情上下文创建，租户 UUID 不来自角色表单。 */
export const managedTenantRoleApi=(tenantId:string)=>api("/bff/platform/tenants/"+encodeURIComponent(tenantId)+"/roles");
