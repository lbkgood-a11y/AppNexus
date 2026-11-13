import { http } from "@/utils/http";
import type { DocumentApi } from "@appnexus/document";

/** 写入沿用统一 CSRF/租户上下文，不对配置修改进行自动重试。 */
async function write(method:"put"|"post",path:string,data:unknown) {
  const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");
  return http.request(method,path,{data,headers:{[csrf.headerName]:csrf.token}});
}
/** 管理模式由构建入口确定，平台接口仍由服务端验证管理员。 */
export function documentApi(platform=false):DocumentApi {
  const base=platform?'/bff/platform/documents':'/api/documents';const id=encodeURIComponent;
  return {
    readiness:type=>http.request('get',`/api/documents/${id(type)}/readiness`),
    pageRelationships:type=>http.request('get',`${base}/${id(type)}/page-relationships`),
    controlPage:(type,revision,operationKey,enabled,reason)=>write('put',`${base}/${id(type)}/page-controls`,{revision,operationKey,enabled,reason}) as ReturnType<DocumentApi['controlPage']>,
    catalog:()=>http.request('get',base),
    activate:(type,revision,enabled)=>write('put',`${base}/${id(type)}/state`,{revision,enabled}),
    tenants:async()=>{const rows:{id:string;name:string}[]=[];let page=1;let total=0;
      do {const result=await http.request<{items:{id:string;name:string}[];total:number}>('get','/bff/platform/tenants',{params:{page,size:100,query:''}});rows.push(...result.items);total=result.total;page++;if(!result.items.length)break;}while(rows.length<total);return rows;},
    grants:type=>http.request('get',`${base}/${id(type)}/grants`),
    grant:(type,tenant,enabled)=>write('put',`${base}/${id(type)}/grants/${id(tenant)}`,{enabled,revision:0}),
    configuration:type=>http.request('get',`${base}/${id(type)}/configuration`),
    workflows:(type,actionCode)=>http.request('get',`${base}/${id(type)}/workflows`,{params:{actionCode}}),
    scenes:(type,actionCode)=>http.request('get',`${base}/${id(type)}/print-scenes`,{params:{actionCode}}),
    save:(type,revision,bindings)=>write('put',`${base}/${id(type)}/configuration`,{revision,bindings}),
    publish:(type,revision)=>write('post',`${base}/${id(type)}/configuration:publish`,{revision})
  };
}
