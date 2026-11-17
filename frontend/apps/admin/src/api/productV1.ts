import {http} from "@/utils/http";
import type {ProductApi} from "@appnexus/master-data/product-types";
const base="/api/master-data/product-v1";
const object=(id:string)=>base+"/"+encodeURIComponent(id);
/** 写入一律带当前CSRF，手动幂等恢复使用原key；不缓存token或自动重试业务写入。 */
async function mutate<T>(method:"post"|"put",url:string,data:object,key?:string):Promise<T>{
 const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
 return http.request<T>(method,url,{data,headers:{[csrf.headerName]:csrf.token,...(key?{"Idempotency-Key":key}:{})}});
}
export const productV1Api:ProductApi={
 operations:()=>http.request("get","/api/operation-decisions"),
 list:query=>http.request("get",base,{params:query}),
 read:id=>http.request("get",object(id)),
 create:data=>mutate("post",base,data),
 update:(id,data)=>mutate("put",object(id),data),
 status:(id,status,version)=>mutate("put",object(id)+"/status",{status,version}),
 archive:(id,version,restore)=>mutate("put",object(id)+(restore?"/draft-restore":"/draft-archive"),{version}),
 changes:(id,page,size)=>http.request("get",object(id)+"/changes",{params:{page,size}}),
 options:(kind,keyword,page)=>http.request("get",base+"/options",{params:{kind,keyword,page,size:20}}),
 configurations:(id,page)=>http.request("get",base+"/categories/"+encodeURIComponent(id)+"/configurations",{params:{page,size:20}}),
 configuration:id=>http.request("get",base+"/configurations/"+encodeURIComponent(id)),
 preview:(id,change)=>mutate("post",object(id)+"/structure-preview",change),
 migrate:(id,change,previewToken,key)=>mutate("put",object(id)+"/structure",{change,previewToken},key)
};
