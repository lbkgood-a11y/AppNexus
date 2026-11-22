import {http} from "@/utils/http";import type {AuthorizationAssignment,AuthorizationCenterApi,PermissionItem,ScopeSupport,FieldDescriptor,DataRecordSearch} from "@appnexus/authorization";
/** 只使用受原业务操作与行范围保护的固定目录，授权编辑器不额外授予查阅权。 */
const recordPaths:Readonly<Record<string,string>>=Object.freeze({
 "masterdata.partner":"/api/master-data/partner",
 "masterdata.warehouse":"/api/master-data/warehouse",
 "masterdata.product":"/api/master-data/product",
 "masterdata.person":"/api/master-data/person",
 "organization.unit":"/api/organization/domain/units"
});
type RecordName={id:string;code:string;name:string};
const recordName=(row:RecordName)=>({id:row.id,name:row.code+" · "+row.name});
const recordSearch:DataRecordSearch={
 resources:Object.keys(recordPaths),
 async resolve(resource,ids){
  if(!Object.hasOwn(recordPaths,resource)||ids.length>100||ids.some(id=>!/^[0-9a-f-]{36}$/i.test(id)))return [];
  const result=await http.request<{recordReferences:{id:string;name?:string}[]}>
    ("get","/api/access/data-policy-metadata",{params:{recordResource:resource,recordIds:ids.join(",")}});
  return result.recordReferences.flatMap(row=>row.name?[{id:row.id,name:row.name}]:[]);
 },
 async search(resource,query,page){
  if(!Object.hasOwn(recordPaths,resource)||query.length>200||!Number.isInteger(page)||page<1)throw new Error("DATA_RECORD_QUERY_INVALID");
  const path=recordPaths[resource];
  if(resource==="organization.unit"){
   const keyword=query.trim().toLocaleLowerCase();
   const rows=(await http.request<RecordName[]>("get",path)).filter(row=>
     row.code.toLocaleLowerCase().includes(keyword)||row.name.toLocaleLowerCase().includes(keyword));
   return {items:rows.slice((page-1)*20,page*20).map(recordName),total:rows.length};
  }
  const result=await http.request<{rows:RecordName[];total:number}>("get",path,{params:{keyword:query,page,size:20}});
  return {items:result.rows.map(recordName),total:result.total};
 }
};
/** 授权全量覆盖写入逐次获取 CSRF；角色版本用于并发控制，不是权限凭据。 */
function api(base:string,catalog:string,metadata:string,organizations=false):AuthorizationCenterApi{return{recordSearch:organizations?recordSearch:undefined,conditionOptions:organizations?()=>http.request("get",metadata+"?conditionOptions=true"):undefined,declarations:organizations?()=>http.request("get",metadata+"?declarations=true"):undefined,templates:{list:role=>http.request("get",base+"/"+encodeURIComponent(role)+"/policy-templates"),save:async(role,name)=>{const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request("post",base+"/"+encodeURIComponent(role)+"/policy-templates",{data:{name},headers:{[csrf.headerName]:csrf.token}});},apply:async(role,templateId,version,reason)=>{const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request("post",base+"/"+encodeURIComponent(role)+"/policy-template-application",{data:{templateId,version,reason},headers:{[csrf.headerName]:csrf.token}});}},menus:()=>http.request("get",organizations?"/api/access/authorization-navigation":"/bff/platform/authorization-navigation"),permissions:()=>http.request<PermissionItem[]>("get",catalog),metadata:()=>http.request<ScopeSupport[]>("get",metadata),fields:()=>http.request<FieldDescriptor[]>("get",metadata.replace("data-policy-metadata","field-policy-metadata")),assignment:role=>http.request<AuthorizationAssignment>("get",base+"/"+encodeURIComponent(role)+"/permissions"),replace:async(role,input)=>{const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request<AuthorizationAssignment>("put",base+"/"+encodeURIComponent(role)+"/permissions",{data:input,headers:{[csrf.headerName]:csrf.token}});},organizationOptions:organizations?()=>http.request("get",metadata+"?organizations=true"):undefined};}
export const tenantAuthorizationApi=api("/api/access/roles","/api/access/permissions","/api/access/data-policy-metadata",true);
export const platformAuthorizationApi=api("/bff/platform/roles","/bff/platform/permissions?scopeType=PLATFORM","/bff/platform/data-policy-metadata?scopeType=PLATFORM");

import type { SubjectAuthorizationApi, SubjectAssignment, EffectivePermission } from "@appnexus/authorization";
import { administrationApi } from "./administration";

/** 主体路径由管理域入口固定；租户自助请求不发送 tenantId，服务端逐次校验上下文。 */
function subjectApi(base: string, subjects: SubjectAuthorizationApi["subjects"]): SubjectAuthorizationApi {
  const path = (id: string) => base + "/" + encodeURIComponent(id);
  return {
    subjects,
    diagnostics:id=>http.request("get",path(id)+"/operation-diagnostics"),
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
/** 使用授权中心自己的租户账号目录；该接口返回完整受控列表，在客户端仅做展示筛选与每页 20 条切片。 */
export const tenantSubjectAuthorizationApi = subjectApi("/api/access/members", async (page, query) => {
  const members = (await http.request<{memberId:string;displayName:string}[]>("get","/api/access/member-options")).map(item=>({id:item.memberId,displayName:item.displayName}));
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
