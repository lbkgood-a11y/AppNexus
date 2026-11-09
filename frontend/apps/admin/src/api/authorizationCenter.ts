import {http} from "@/utils/http";import type {AuthorizationAssignment,AuthorizationCenterApi,PermissionItem,ScopeSupport,FieldDescriptor} from "@appnexus/authorization";import {organizationApi} from "./organization";
/** 授权全量覆盖写入逐次获取 CSRF；角色版本用于并发控制，不是权限凭据。 */
function api(base:string,catalog:string,metadata:string,organizations=false):AuthorizationCenterApi{return{menus:()=>http.request("get",organizations?"/api/access/authorization-navigation":"/bff/platform/authorization-navigation"),permissions:()=>http.request<PermissionItem[]>("get",catalog),metadata:()=>http.request<ScopeSupport[]>("get",metadata),fields:()=>http.request<FieldDescriptor[]>("get",metadata.replace("data-policy-metadata","field-policy-metadata")),assignment:role=>http.request<AuthorizationAssignment>("get",base+"/"+encodeURIComponent(role)+"/permissions"),replace:async(role,input)=>{const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request<AuthorizationAssignment>("put",base+"/"+encodeURIComponent(role)+"/permissions",{data:input,headers:{[csrf.headerName]:csrf.token}});},organizationOptions:organizations?async()=>organizationApi.treeUnits().then(items=>items.filter(item=>item.enabled).map(item=>({id:item.id,name:item.name}))):undefined};}
export const tenantAuthorizationApi=api("/api/access/roles","/api/access/permissions","/api/access/data-policy-metadata",true);
export const platformAuthorizationApi=api("/bff/platform/roles","/bff/platform/permissions?scopeType=PLATFORM","/bff/platform/data-policy-metadata?scopeType=PLATFORM");

import type { SubjectAuthorizationApi, SubjectAssignment, EffectivePermission } from "@appnexus/authorization";
import { administrationApi } from "./administration";

/** 主体路径由管理域入口固定；租户自助请求不发送 tenantId，服务端逐次校验上下文。 */
function subjectApi(base: string, subjects: SubjectAuthorizationApi["subjects"]): SubjectAuthorizationApi {
  const path = (id: string) => base + "/" + encodeURIComponent(id);
  return {
    subjects,
    assignment: id => http.request<SubjectAssignment>("get", path(id) + "/roles"),
    effective: id => http.request<EffectivePermission[]>("get", path(id) + "/effective-permissions"),
    replace: async (id, input) => {
      const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
      return http.request<SubjectAssignment>("put", path(id) + "/roles", { data: input, headers: { [csrf.headerName]: csrf.token } });
    }
  };
}
/** 平台目录每页 20 条，显示真实账号状态，不把平台角色分配到租户成员。 */
export const platformSubjectAuthorizationApi = subjectApi("/bff/platform/users", async (page, query) => {
  const result = await administrationApi.users(page, query);
  return { total: result.total, items: result.items.map(item => ({ id: item.id, name: item.displayName, detail: item.username, enabled: item.enabled })) };
});
/** 复用租户组织成员目录；该接口返回完整受控列表，在客户端仅做展示筛选与每页 20 条切片。 */
export const tenantSubjectAuthorizationApi = subjectApi("/api/access/members", async (page, query) => {
  const members = await organizationApi.directoryMembers();
  const matched = members.filter(item => item.displayName.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
  return { total: matched.length, items: matched.slice((page - 1) * 20, page * 20).map(item => ({ id: item.id, name: item.displayName })) };
});

/** 成员独立范围接口使用可信租户会话，完整快照写入逐次获取 CSRF。 */
tenantSubjectAuthorizationApi.grants = {
  get: member => http.request("get", "/api/access/members/" + encodeURIComponent(member) + "/authorizations"),
  replace: async (member, input) => {
    const csrf = await http.request<{token:string;headerName:string}>("get", "/bff/csrf");
    return http.request("put", "/api/access/members/" + encodeURIComponent(member) + "/authorizations", {data:input,headers:{[csrf.headerName]:csrf.token}});
  }
};
