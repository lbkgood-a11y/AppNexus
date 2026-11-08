import {http} from "@/utils/http";
import type {BusinessDictionary,DictionaryItem,DictionaryManagementApi} from "@appnexus/configuration";

const base="/api/configuration/dictionaries";
/** 字典写操作逐次取得 CSRF；租户版本由统一 HTTP 入口附加，表单不能指定租户。 */
async function write<T>(method:"post"|"put"|"delete",path:string,data?:object,params?:object){const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");return http.request<T>(method,base+path,{data,params,headers:{[csrf.headerName]:csrf.token}});}
export const dictionaryManagementApi:DictionaryManagementApi={
 list:()=>http.request<BusinessDictionary[]>("get",base),create:input=>write("post","",input),update:(id,input)=>write("put","/"+encodeURIComponent(id),input),remove:(id,version)=>write("delete","/"+encodeURIComponent(id),undefined,{version}),
 items:id=>http.request<DictionaryItem[]>("get",base+"/"+encodeURIComponent(id)+"/items"),createItem:(id,input)=>write("post","/"+encodeURIComponent(id)+"/items",input),updateItem:(id,itemId,input)=>write("put","/"+encodeURIComponent(id)+"/items/"+encodeURIComponent(itemId),input),removeItem:(id,itemId,version)=>write("delete","/"+encodeURIComponent(id)+"/items/"+encodeURIComponent(itemId),undefined,{version})
};
