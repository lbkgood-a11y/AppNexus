import {http} from "@/utils/http";
import type {ProductCatalogApi,CatalogKind,CatalogRow} from "@appnexus/master-data/product-catalog-types";
/** 固定四资源白名单，租户/操作者由Cookie会话确定，禁止客户端生成任意路径。 */
const resources:Record<CatalogKind,string>={unit:"product-unit",category:"product-category",attribute:"product-attribute",template:"product-template"};
const path=(kind:CatalogKind)=>"/api/master-data/"+resources[kind];
/** 每次写入使用当前CSRF，失败不自动重放；版本由领域页面取自真实记录。 */
async function write<T>(method:"post"|"put",url:string,data:object):Promise<T>{
 const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
 return http.request<T>(method,url,{data,headers:{[csrf.headerName]:csrf.token}});
}
export const productCatalogApi:ProductCatalogApi={
 operations:()=>http.request("get","/api/operation-decisions"),
 list:async(kind,query)=>{
 const result=await http.request<{rows:CatalogRow[];total:number}|CatalogRow[]>("get",path(kind),{params:query});
 if(!Array.isArray(result))return result;
 // 旧配置摘要当前接口返回完整列表；只在客户端做同语义分页，不伪造服务器total。
 const filtered=result.filter(r=>(r.code??"").includes(query.keyword)||(r.name??"").includes(query.keyword));
 return {rows:filtered.slice((query.page-1)*query.size,query.page*query.size),total:filtered.length};
 },
 create:(kind,data)=>write("post",path(kind),data),
 update:(kind,id,data)=>write("put",path(kind)+"/"+encodeURIComponent(id),data),
 status:(kind,id,data)=>write("put",path(kind)+"/"+encodeURIComponent(id)+"/status",data),
 versions:(kind,id)=>http.request("get",path(kind)+"/"+encodeURIComponent(id)+(kind==="attribute"?"/revisions":"/versions")),
 publishVersion:(kind,id,version)=>write("post",path(kind)+(kind==="attribute"?"/revisions/":"/versions/")+encodeURIComponent(id)+"/publish?version="+version,{}),
 createRevision:(id,data)=>write("post",path("attribute")+"/"+encodeURIComponent(id)+"/revisions",data),
 createVersion:(id,data)=>write("post",path("template")+"/"+encodeURIComponent(id)+"/versions",data),
 publishConfiguration:(id,data)=>write("post",path("category")+"/"+encodeURIComponent(id)+"/configurations",data)
};
