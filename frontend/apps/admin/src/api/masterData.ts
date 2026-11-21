import { executeObjectCommand } from "./objectExecution";
import { http } from "@/utils/http";
import type { MasterDataApi, MasterDataKind } from "@appnexus/master-data";
/** 固定主数据路径，租户与操作者只由服务端会话确定。 */
const path=(kind:MasterDataKind,id?:string)=>"/api/master-data/"+kind+(id?"/"+encodeURIComponent(id):"");
/** 写入使用当前会话 CSRF；版本防覆盖，重复编码防重复创建，不自动重放写请求。 */
async function mutate<T>(method:"post"|"put"|"delete",url:string,data:object):Promise<T>{
  const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
  return http.request<T>(method,url,{data,headers:{[csrf.headerName]:csrf.token}});
}
/** 真实后端适配器；操作入口来自统一准入，读取及写入仍在业务服务复核。 */
export const masterDataApi:MasterDataApi={
  codingOptions:(type,field)=>http.request("get",`/api/business-objects/${encodeURIComponent(type)}/coding-options`,{params:{field}}),
  issueCode:(type,key,requestId)=>executeObjectCommand(`/api/business-objects/${encodeURIComponent(type)}/encoders/${encodeURIComponent(key)}/issue`,{},requestId),
  partnerAccess:()=>http.request("get",path("partner"),{params:{dataAccess:true}}),
  geographyDirectory:kind=>http.request("get",path(kind),{params:{geography:true}}),
  readPartner:async id=>{const result=await http.request<{rows:import("@appnexus/master-data").MasterDataRow[]}>("get",path("partner"),{params:{id,page:1,size:1}});if(!result.rows[0])throw new Error("MASTER_DATA_CHANGED");return result.rows[0];},
  readWarehouse:async id=>{const result=await http.request<{rows:import("@appnexus/master-data").MasterDataRow[]}>("get",path("warehouse"),{params:{id,page:1,size:1}});if(!result.rows[0])throw new Error("MASTER_DATA_CHANGED");return result.rows[0];},
  warehouseMapConfig:()=>http.request("get","/api/maps/_AMapService/configuration"),
  warehouseOptions:()=>http.request("get",path("warehouse"),{params:{options:true}}),
  warehouseFieldRestrictions:()=>http.request("get",`${path("warehouse")}/field-restrictions`),
  partnerOptions:()=>http.request("get",path("partner"),{params:{options:true}}),
  geographyOptions:(kind,query)=>http.request("get",path(kind),{params:{standards:true,...query}}),
  geographyTree:(kind,query)=>http.request("get",path(kind),{params:{tree:true,...query}}),
  operations:()=>http.request<string[]>("get","/api/operation-decisions"),
  list:(kind,query)=>http.request("get",path(kind),{params:query}),
  create:(kind,input)=>mutate("post",path(kind),input),
  update:(kind,id,input)=>mutate("put",path(kind,id),input),
  setStatus:(kind,id,input)=>mutate("put",path(kind,id)+"/status",input)
};

