import {http} from "@/utils/http";
import type {ProductApi} from "@appnexus/master-data/product-types";
const base="/api/master-data/product";
const object=(id:string)=>base+"/"+encodeURIComponent(id);
/** 写入一律带当前CSRF，手动幂等恢复使用原key；不缓存token或自动重试业务写入。 */
async function mutate<T>(method:"post"|"put",url:string,data:object,key?:string):Promise<T>{
 const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
 return http.request<T>(method,url,{data,headers:{[csrf.headerName]:csrf.token,...(key?{"Idempotency-Key":key}:{})}});
}
export const productV1Api:ProductApi={
 images:id=>http.request("get",object(id)+"/images"),
 imageUrl:(id,image)=>object(id)+"/images/"+encodeURIComponent(image)+"/content",
 uploadImage:async(id,version,file)=>{const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");return http.request("post",object(id)+"/images",{params:{version},data:file,headers:{[csrf.headerName]:csrf.token,"Content-Type":file.type}});},
 detachImage:(id,image,version)=>mutate("put",object(id)+"/images/"+encodeURIComponent(image)+"/detach",{version}),
 structure:id=>http.request("get",object(id)+"/structure-records"),
 createSpecification:(id,data)=>mutate("post",object(id)+"/structure-records/specifications",data),
 createSku:(id,data)=>mutate("post",object(id)+"/structure-records/skus",data),
 addComponent:(id,data)=>mutate("post",object(id)+"/structure-records/components",data),
 structureState:(id,kind,row,data)=>mutate("put",object(id)+"/structure-records/"+kind+"/"+encodeURIComponent(row)+"/status",data),
 approvalSnapshot:(id,taskId)=>http.request("get",base+"/approval-requests/"+encodeURIComponent(id),{params:{taskId}}),
 approvals:id=>http.request("get",object(id)+"/approvals"),
 approvalOrganizations:()=>http.request("get",base+"/approval-organizations"),
 submitApproval:(id,version,organizationId,key)=>mutate("post",object(id)+"/approval",{version,organizationId},key),
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
 reconcileLegacy:(page,size)=>http.request("get",base+"/legacy/reconciliation",{params:{page,size}}),
 stageLegacy:id=>mutate("post",base+"/legacy/"+encodeURIComponent(id)+"/stage",{}),
 reviewLegacy:(id,disposition,reason,version)=>mutate("put",object(id)+"/legacy-specification-review",{disposition,reason,version}),
 migrate:(id,change,previewToken,key)=>mutate("put",object(id)+"/structure",{change,previewToken},key)
};
