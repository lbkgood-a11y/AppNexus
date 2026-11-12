import { chromium, type Browser } from "playwright-core";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { readConfig, type AgentConfig } from "./config.js";
import { QueueFailure, queueCommand, runQueue } from "./queue.js";
import { renderPdf, type ClaimedJob } from "./render.js";
import { pathToFileURL } from "node:url";
import { discoverPrinters } from "./printers.js";

let config: AgentConfig;
let token="",expires=0;
async function accessToken(){if(Date.now()<expires-30_000)return token;const body=new URLSearchParams({grant_type:"client_credentials",scope:"print-agent:operate"});const response=await fetch(config.tokenUrl,{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded",authorization:"Basic "+Buffer.from(encodeURIComponent(config.clientId)+":"+encodeURIComponent(config.clientSecret)).toString("base64")},body,redirect:"error",signal:AbortSignal.timeout(30_000)});if(!response.ok)throw new Error(`Token request failed: ${response.status}`);const value=await response.json() as {access_token:string;expires_in?:number};token=value.access_token;expires=Date.now()+(value.expires_in||300)*1000;return token}
/** 平台写调用使用新幂等键；只记录状态码，不输出 Token、模板、快照或设备队列详情。 */
async function call<T>(path:string,body?:unknown):Promise<T>{const response=await fetch(config.api+path,{method:"POST",headers:{authorization:`Bearer ${await accessToken()}`,"content-type":"application/json","Idempotency-Key":crypto.randomUUID(),"X-Tenant-Context-Version":config.tenantVersion},body:body===undefined?undefined:JSON.stringify(body),redirect:"error",signal:AbortSignal.timeout(30_000)});if(response.status===204)return undefined as T;if(!response.ok)throw new Error(`Print API failed: ${response.status}`);return response.json() as Promise<T>}
/** 将 PDF 提交指定队列；参数数组不经过 shell，队列名不能注入额外命令。 */
async function submit(file:string,printer:string,copies:number){const {command,args}=queueCommand(process.platform,file,printer,copies,config.sumatra);await runQueue(command,args)}
async function receipt(id:string,state:"SUBMITTED"|"FAILED"|"UNKNOWN",code:string){await call(`/api/printing/agent/jobs/${encodeURIComponent(id)}/receipt`,{state,agent:config.agent,result:{code}})}
/** 渲染失败确定未提交；调用队列后发生任何异常均保守记 UNKNOWN，绝不自动重打。 */
async function execute(browser:Browser,job:ClaimedJob){
  let directory:string|undefined;
  let submitting=false;
  try{
    directory=await mkdtemp(join(tmpdir(),"appnexus-print-"));
    const file=join(directory,"document.pdf");
    await renderPdf(browser,job,file);
    if(job.output_type==="PDF"){
      // PDF 任务不触碰设备队列；上传失败保留领取租约，防止成功产物被失败回执覆盖。
      submitting=true;
      const response=await fetch(config.api+`/api/printing/agent/jobs/${encodeURIComponent(job.id)}/pdf?agent=${encodeURIComponent(config.agent)}`,{
        method:"POST",redirect:"error",signal:AbortSignal.timeout(30_000),
        headers:{authorization:`Bearer ${await accessToken()}`,"content-type":"application/pdf","Idempotency-Key":crypto.randomUUID(),"X-Tenant-Context-Version":config.tenantVersion},
        body:await readFile(file)
      });
      if(!response.ok)throw new Error("PDF_UPLOAD_FAILED");
      return;
    }
    submitting=true;
    await submit(file,job.system_name,job.copies);
    await receipt(job.id,"SUBMITTED","SYSTEM_QUEUE_ACCEPTED");
  }catch(error){
    const uncertain=submitting&&!(error instanceof QueueFailure&&!error.uncertain);
    const code=uncertain?"QUEUE_RESULT_UNKNOWN":submitting?"QUEUE_NOT_STARTED":"RENDER_FAILED";
    try{await receipt(job.id,uncertain?"UNKNOWN":"FAILED",code)}catch{
      // 回执失败保留平台租约，由过期处理转 UNKNOWN；代理不重新执行任务。
    }
  }finally{if(directory)await rm(directory,{recursive:true,force:true})}
}
async function main(){config=readConfig(process.env);const browser=await chromium.launch({executablePath:config.chromium,headless:true,args:["--disable-background-networking","--disable-component-update","--no-default-browser-check"]});const stop=async()=>{await browser.close();process.exit(0)};process.once("SIGINT",stop);process.once("SIGTERM",stop);let queueReportAt=0;for(;;){try{if(Date.now()-queueReportAt>60_000){try{const queues=await discoverPrinters();await call("/api/printing/agent/queues",{site:config.site,agent:config.agent,queues:queues.map(value=>value.name)})}catch{console.error("PRINT_QUEUE_REPORT_FAILED")}queueReportAt=Date.now()}const job=await call<ClaimedJob|null>(`/api/printing/agent/claim?site=${encodeURIComponent(config.site)}&agent=${encodeURIComponent(config.agent)}`);if(job)await execute(browser,job)}catch(error){console.error(error instanceof Error?error.message:"Print agent failure")}await new Promise(resolve=>setTimeout(resolve,config.pollMs))}}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  if(process.argv.includes("--list-printers")) console.log(JSON.stringify(await discoverPrinters(),null,2));
  else await main();
}
