import { http } from "@/utils/http";
import type { WorkflowApi } from "@appnexus/workflow";
const base = "/api/workflow";
/** 写操作只获取CSRF，不自动网络重试；租户版本由公共HTTP入口附加。 */
async function write<T>(
  method: "post" | "put",
  path: string,
  data: unknown,
): Promise<T> {
  const csrf = await http.request<{ token: string; headerName: string }>(
    "get",
    "/bff/csrf",
  );
  return http.request<T>(method, base + path, {
    data,
    headers: {
      [csrf.headerName]: csrf.token,
      "Idempotency-Key": crypto.randomUUID(),
    },
  });
}
const get = <T>(path: string) => http.request<T>("get", base + path);
const id = encodeURIComponent;
/** 应用HTTP适配，领域组件不能直接指定租户或身份。 */
export const workflowApi: WorkflowApi = {
  preview: (model, sample) => write("post", "/preview", { model, sample }),
  withdraw: (key, reason) =>
    write("post", "/instances/" + id(key) + "/withdraw", { reason }),
  capabilities: () => get("/capabilities"),
  businesses: () => get("/businesses"),
  definitions: () => get("/definitions"),
  save: (key, revision, model) =>
    write("put", "/definitions/" + id(key), { revision, model }),
  validate: (model) => write("post", "/validate", model),
  publish: (key, revision) =>
    write("post", "/definitions/" + id(key) + "/publish", { revision }),
  disable: (key, revision, reason) =>
    write("post", "/definitions/" + id(key) + "/disable", { revision, reason }),
  versions: (key) => get("/definitions/" + id(key) + "/versions"),
  tasks: (view) => get("/tasks?view=" + id(view)),
  task: (key) => get("/tasks/" + id(key)),
  detail: (key) => get("/instances/" + id(key)),
  instances: () => get("/instances"),
  complete: (key, action, values, comment = "") =>
    write("post", "/tasks/" + id(key) + "/actions", {
      action,
      values,
      comment,
    }),
  reassign: (key, actor, reason) =>
    write("post", "/tasks/" + id(key) + "/reassign", { actor, reason }),
  retry: (key, reason) =>
    write("post", "/jobs/" + id(key) + "/retry", { reason }),
};
