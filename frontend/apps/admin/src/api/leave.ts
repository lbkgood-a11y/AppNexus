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
  actions:()=>http.request('get','/api/leave-poc/actions'),
  organizations: () => http.request("get", "/api/leave-poc/organizations"),
  list: () => http.request("get", "/api/leave-poc"),
  create: (input) => write("", input),
  submit: (id) => write("/" + encodeURIComponent(id) + "/submit"),
  update: (id,basisVersion,input)=>write('/'+encodeURIComponent(id),{basisVersion,input},'put'),
  cycles: id=>http.request('get','/api/leave-poc/'+encodeURIComponent(id)+'/cycles'),
  read: (id, taskId) =>
    http.request("get", "/api/leave-poc/" + encodeURIComponent(id), {
      params: taskId ? { taskId } : {},
    }),
};
