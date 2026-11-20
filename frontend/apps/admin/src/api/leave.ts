import { executeObjectOperation, executeObjectCommand } from "./objectExecution";
import { http } from "@/utils/http";
import type { LeaveApi } from "@appnexus/workflow";

/** 请假写入沿用 CSRF 和可信租户 HTTP 入口；只在用户操作时请求，不自动重试。 */
async function write<T>(path: string, data?: unknown, method:"post"|"put"="post"): Promise<T> {
  const csrf = await http.request<{ token: string; headerName: string }>(
    "get",
    "/bff/csrf",
  );
  return http.request<T>(method, "/api/leave-poc" + path, {
    data,
    headers: {
      [csrf.headerName]: csrf.token,
      "Idempotency-Key": crypto.randomUUID(),
    },
  });
}

/** 只调用真实业务 API，不在浏览器推断或写入审批结果。 */
export const leaveApi: LeaveApi = {
  timeline:id=>http.request('get','/api/leave-poc/'+encodeURIComponent(id)+'/timeline'),
  /** 统一对象诊断只使用当前会话，不传入目标账号；归属与配置检查由业务提供者执行。 */
  availability:async id=>Promise.all(['submit','print'].map(async operationCode=>{
    const operationId='LeaveRequest.'+operationCode;
    const checks=await http.request<{layer:string;status:string;reason:string}[]>('get','/api/operations/'+encodeURIComponent(operationId)+'/objects/'+encodeURIComponent(id)+'/diagnostics');
    const blocked=checks.find(check=>check.status!=='READY'&&check.status!=='NOT_APPLICABLE');
    return {operationCode,operationId,allowed:!blocked,reasonCode:blocked?.reason??'READY'};
  })),
  actions:()=>http.request('get','/api/leave-poc/actions'),
  organizations: () => http.request("get", "/api/leave-poc/organizations"),
  list: () => http.request("get", "/api/leave-poc"),
  create: (input) => write("", input),
  submit: (id,operationId) => executeObjectCommand("/api/leave-poc/" + encodeURIComponent(id) + "/submit",{operationId}),
  print:async(id,operationId)=>{
    const row=await http.request<{basis_version:number}>("get","/api/leave-poc/"+encodeURIComponent(id));
    const result=await executeObjectOperation({objectType:"LeaveRequest",objectId:id,operationCode:"print",operationId,basisVersion:row.basis_version});
    return result.output as {id:string;previewUrl:string};
  },
  update: (id,basisVersion,input)=>write('/'+encodeURIComponent(id),{basisVersion,input},'put'),
  cycles: id=>http.request('get','/api/leave-poc/'+encodeURIComponent(id)+'/cycles'),
  read: (id, taskId) =>
    http.request("get", "/api/leave-poc/" + encodeURIComponent(id), {
      params: taskId ? { taskId } : {},
    }),
};
