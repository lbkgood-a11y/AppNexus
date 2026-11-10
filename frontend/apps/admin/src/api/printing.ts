import { http } from "@/utils/http";
import type { DeviceDraft, PrintApi, TemplateDraft } from "@appnexus/print";
const base="/api/printing",id=encodeURIComponent;
/** 写操作统一获取 CSRF 并使用一次性幂等 UUID；网络错误不自动重放打印。 */
async function write<T>(method:"post"|"put",path:string,data:unknown):Promise<T>{
  const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");
  return http.request<T>(method,base+path,{data,headers:{[csrf.headerName]:csrf.token,"Idempotency-Key":crypto.randomUUID()}});
}
const get=<T>(path:string)=>http.request<T>("get",base+path);
/** 打印领域应用适配；组件不能传入租户或操作者。 */
export const printingApi:PrintApi={
  capabilities:()=>get("/capabilities"),templates:()=>get("/templates"),template:key=>get("/templates/"+id(key)),
  saveTemplate:(key,draft:TemplateDraft)=>write(key?"put":"post",key?"/templates/"+id(key):"/templates",draft),
  publish:(key,revision)=>write("post","/templates/"+id(key)+"/publish",{revision}),versions:key=>get("/templates/"+id(key)+"/versions"),
  devices:()=>get("/devices"),saveDevice:(key,draft:DeviceDraft)=>write(key?"put":"post",key?"/devices/"+id(key):"/devices",draft),
  jobs:()=>get("/jobs"),reprint:(key,reason)=>write("post","/jobs/"+id(key)+"/reprint",{reason}),
};
