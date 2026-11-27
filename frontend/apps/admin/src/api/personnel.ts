import {http} from "@/utils/http";
import type {PersonnelApi} from "@appnexus/master-data/organization";
/** 固定已发布人员 HTTP 路径；入口归并不更改已有权限和可信身份契约。 */
const path=(_kind:"person",id?:string)=>"/api/master-data/person"+(id?"/"+encodeURIComponent(id):"");
/** 逐次读取 CSRF，保留人员版本校验，不自动重放写入。 */
async function mutate<T>(method:"post"|"put"|"delete",url:string,data:object):Promise<T>{const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");return http.request<T>(method,url,{data,headers:{[csrf.headerName]:csrf.token}});}
/** 人员专用真实 API；ID 与固定分类经路径编码，不从客户端接收租户、操作者或权限码。 */
export const personnelApi:PersonnelApi={
  fieldRestrictions:()=>http.request("get",path("person")+"/field-restrictions"),
  geographyDirectory:()=>http.request("get",path("person")+"/options",{params:{geography:true}}),
  operations:()=>http.request<string[]>("get","/api/operation-decisions"),
  list:(_kind,query)=>http.request("get",path("person"),{params:query}),
  create:(_kind,input)=>mutate("post",path("person"),input),
  update:(_kind,id,input)=>mutate("put",path("person",id),input),
  setStatus:(_kind,id,input)=>mutate("put",path("person",id)+"/status",input),
  read:id=>http.request("get",path("person",id)),
  options:()=>http.request("get",path("person")+"/options"),
  saveDetail:(person,section,id,input)=>mutate(id?"put":"post",path("person",person)+"/details/"+encodeURIComponent(section)+(id?"/"+encodeURIComponent(id):""),input),
  deleteDetail:(person,section,id,version)=>mutate("delete",path("person",person)+"/details/"+encodeURIComponent(section)+"/"+encodeURIComponent(id)+"?version="+version,{}),
  assign:(person,id,input)=>mutate(id?"put":"post",path("person",person)+"/users"+(id?"/"+encodeURIComponent(id):""),input),
  unassign:(person,id,version)=>mutate("delete",path("person",person)+"/users/"+encodeURIComponent(id)+"?version="+version,{})
};
