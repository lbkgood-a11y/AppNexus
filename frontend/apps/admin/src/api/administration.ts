import {http} from "@/utils/http";
import type {AdministrationApi,DirectoryUser,DirectoryTenant,DirectoryMember,DirectoryPage} from "@appnexus/tenant/administration";
/** 固定平台端点写入都读取当前 CSRF，不携带客户端操作者或租户业务上下文。 */
async function write(method:"post"|"put",path:string,data:object){
 const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");
 return http.request(method,"/bff/platform"+path,{data,headers:{[csrf.headerName]:csrf.token}});
}
/** 每页固定 20 条，后端权威分页；筛选词作为参数绑定。 */
export const administrationApi:AdministrationApi={
 access:()=>http.request("get","/bff/platform/access"),
 users:(page,query)=>http.request<DirectoryPage<DirectoryUser>>("get","/bff/platform/users",{params:{page,size:20,query}}),
 tenants:(page,query)=>http.request<DirectoryPage<DirectoryTenant>>("get","/bff/platform/tenants",{params:{page,size:20,query}}),
 members:(tenant,page,query)=>http.request<DirectoryPage<DirectoryMember>>("get",`/bff/platform/tenants/${encodeURIComponent(tenant)}/members`,{params:{page,size:20,query}}),
 createUser:(username,displayName,password)=>write("post","/users",{username,displayName,password}) as Promise<DirectoryUser>,
 createTenant:(requestId,name,administratorAccountId)=>write("post","/tenants",{requestId,name,administratorAccountId}) as Promise<DirectoryTenant>,
 addMember:(tenant,accountId)=>write("post",`/tenants/${encodeURIComponent(tenant)}/members`,{accountId}),
 userState:(id,enabled)=>write("put",`/users/${encodeURIComponent(id)}/state`,{enabled}),
 tenantState:(id,enabled)=>write("put",`/tenants/${encodeURIComponent(id)}/state`,{enabled}),
 memberState:(tenant,member,enabled)=>write("put",`/tenants/${encodeURIComponent(tenant)}/members/${encodeURIComponent(member)}/state`,{enabled})
};
