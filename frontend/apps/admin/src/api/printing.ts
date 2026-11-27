import { http } from "@/utils/http";
import { createPrintId, type DeviceDraft, type PrintApi, type TemplateDraft } from "@appnexus/print";
const base="/api/printing",id=encodeURIComponent;
/** 写操作统一获取 CSRF 并使用一次性幂等 UUID；网络错误不自动重放打印。 */
async function write<T>(method:"post"|"put",path:string,data:unknown,requestId:string=createPrintId()):Promise<T>{
  const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");
  return http.request<T>(method,base+path,{data,headers:{[csrf.headerName]:csrf.token,"Idempotency-Key":requestId}});
}
const get=<T>(path:string)=>http.request<T>("get",base+path);
/** 打印领域应用适配；组件不能传入租户或操作者。 */
export const printingApi:PrintApi={
  preflight:(key,mode,deviceId)=>http.request("get",base+"/documents/"+id(key)+"/preflight",{params:{mode,deviceId}}),
  createOutput:(key,data,requestId)=>write("post","/documents/"+id(key)+"/outputs",data,requestId),
  outputStatus:key=>get("/outputs/"+id(key)),outputPdf:key=>http.request<Blob>("get",base+"/outputs/"+id(key)+"/pdf",{responseType:"blob"}),
  confirmOutput:(key,deviceId,requestId)=>write("post","/outputs/"+id(key)+"/confirm",{deviceId},requestId),
  scenes:()=>get("/scenes"),saveScene:(key,data)=>write(key?"put":"post",key?"/scenes/"+id(key):"/scenes",data),
  documents:()=>get("/documents"),saveDocument:data=>write("post","/documents",data),
  printDocument:(key,data,requestId)=>write("post","/documents/"+id(key)+"/print",data,requestId),
  clientQueues:()=>get("/client-queues"),
  designContracts:()=>get("/design-contracts"),
  createContract:data=>write("post","/design-contracts",data),
  // 页面分组名称保留领域语义，但权限来源统一为操作目录；不再读取打印独立权限映射。
  capabilities:async()=>{
    const operations=new Set(await http.request<string[]>("get","/api/operation-decisions"));
    return Object.entries({read:"PrintTemplate.list",design:"PrintTemplate.update",publish:"PrintTemplate.publish",devices:"PrintDevice.update",jobs:"PrintJob.list",submit:"PrintOutput.create",reprint:"PrintJob.reprint"})
      .filter(([,operation])=>operations.has(operation)).map(([name])=>name);
  },templates:()=>get("/templates"),template:key=>get("/templates/"+id(key)),
  saveTemplate:(key,draft:TemplateDraft)=>write(key?"put":"post",key?"/templates/"+id(key):"/templates",draft),
  publish:(key,revision)=>write("post","/templates/"+id(key)+"/publish",{revision}),versions:key=>get("/templates/"+id(key)+"/versions"),
  devices:()=>get("/devices"),saveDevice:(key,draft:DeviceDraft)=>write(key?"put":"post",key?"/devices/"+id(key):"/devices",draft),
  jobs:()=>get("/jobs"),reprint:(key,reason)=>write("post","/jobs/"+id(key)+"/reprint",{reason}),
  downloadPdf:key=>http.request<Blob>("get",base+"/jobs/"+id(key)+"/pdf",{responseType:"blob"}),
  restoreVersion:(key,version,revision)=>write("post","/templates/"+id(key)+"/restore",{version,revision}),
};
