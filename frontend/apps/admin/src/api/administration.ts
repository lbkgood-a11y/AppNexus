import {http} from "@/utils/http";
import type {AdministrationApi,DirectoryUser,DirectoryTenant,DirectoryMember,DirectoryPage,IntegrationClient,IntegrationSecret} from "@appnexus/tenant/administration";
/** 固定平台端点写入都读取当前 CSRF，不携带客户端操作者或租户业务上下文。 */
async function write(method:"post"|"put"|"delete",path:string,data:object={}){
 const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");
 return http.request(method,"/bff/platform"+path,{data,headers:{[csrf.headerName]:csrf.token}});
}
/** 每页固定 20 条，后端权威分页；筛选词作为参数绑定。 */
export const administrationApi:AdministrationApi={
 access:()=>http.request("get","/bff/platform/access"),
 users:(page,query)=>http.request<DirectoryPage<DirectoryUser>>("get","/bff/platform/users",{params:{page,size:20,query}}),
 tenants:(page,query)=>http.request<DirectoryPage<DirectoryTenant>>("get","/bff/platform/tenants",{params:{page,size:20,query}}),
 members:(tenant,page,query)=>http.request<DirectoryPage<DirectoryMember>>("get",`/bff/platform/tenants/${encodeURIComponent(tenant)}/members`,{params:{page,size:20,query}}),
 createUser:(username,displayName,email,phoneNumber,password)=>write("post","/users",{username,displayName,email:email||null,phoneNumber:phoneNumber||null,password}) as Promise<DirectoryUser>,
 updateUser:(id,displayName,email,phoneNumber)=>write("put",`/users/${encodeURIComponent(id)}`,{displayName,email:email||null,phoneNumber:phoneNumber||null}) as Promise<DirectoryUser>,
 createTenant:(requestId,name,administratorAccountId)=>write("post","/tenants",{requestId,name,administratorAccountId}) as Promise<DirectoryTenant>,
 updateTenant:(id,name)=>write("put",`/tenants/${encodeURIComponent(id)}`,{name}) as Promise<DirectoryTenant>,
 addMember:(tenant,accountId)=>write("post",`/tenants/${encodeURIComponent(tenant)}/members`,{accountId}),
 inviteMember:(tenant,accountId)=>write("post",`/tenants/${encodeURIComponent(tenant)}/invitations`,{accountId}) as Promise<{invitation:{id:string};token:string}>,
 userState:(id,enabled)=>write("put",`/users/${encodeURIComponent(id)}/state`,{enabled}),
 revokeUserSessions:id=>write("post",`/users/${encodeURIComponent(id)}/revoke-sessions`),
 issuePasswordRecovery:id=>write("post",`/users/${encodeURIComponent(id)}/password-recovery`) as Promise<{token:string}>,
 clients:tenant=>http.request<IntegrationClient[]>("get",`/bff/platform/tenants/${encodeURIComponent(tenant)}/clients`),
 createClient:(tenant,name,scopes)=>write("post",`/tenants/${encodeURIComponent(tenant)}/clients`,{name,scopes}) as Promise<IntegrationSecret>,
 clientState:(client,enabled)=>write("put",`/clients/${encodeURIComponent(client.id)}`,{version:client.version,enabled,scopes:client.scopes}) as Promise<IntegrationClient>,
 rotateClient:client=>write("post",`/clients/${encodeURIComponent(client.id)}/secret-rotation`,{version:client.version}) as Promise<IntegrationSecret>,
 tenantState:(id,enabled)=>write("put",`/tenants/${encodeURIComponent(id)}/state`,{enabled}),
 memberState:(tenant,member,enabled)=>write("put",`/tenants/${encodeURIComponent(tenant)}/members/${encodeURIComponent(member)}/state`,{enabled}),
 removeMember:(tenant,member)=>write("delete",`/tenants/${encodeURIComponent(tenant)}/members/${encodeURIComponent(member)}`)
};
