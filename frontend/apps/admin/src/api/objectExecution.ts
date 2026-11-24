import { http } from "@/utils/http";

/** 对象能力只提交身份、操作和依据版本，流程/模板选择由服务端关联配置驱动。 */
export interface ObjectExecutionRequest {
  objectType: string;
  objectId: string;
  operationCode: string;
  operationId: string;
  basisVersion: number;
}
/** 真实专业能力输出可能包含尚未产生的设备、回执等空值。 */
export interface ObjectExecutionResult {
  resolution: { id: string; service: string; configurationVersion: number };
  output: Record<string, unknown>;
}
/**
 * 显式执行且不自动重试；调用方需要重放时必须保留原请求键及相同意图。
 * @param input 已保存记录的声明操作
 * @param requestId UUID幂等键，缺省在本次用户操作生成
 */
export async function executeObjectOperation(input: ObjectExecutionRequest, requestId: string = crypto.randomUUID()): Promise<ObjectExecutionResult> {
  return executeObjectCommand("/api/business-object-executions", input, requestId);
}
/**
 * 对象操作共用的写入传输；保留业务准备命令的边界，不将保存、冻结或发号拆成多个浏览器事务。
 * @param path 构建内固定的对象命令路由，禁止来自配置或远程响应
 * @param input 命令输入，不包含可信主体或能力快照
 * @param requestId 同一用户命令的UUID，重试保留原值
 */
export async function executeObjectCommand<T>(path: string, input: unknown, requestId: string = crypto.randomUUID()): Promise<T> {
  const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
  return http.request("post", path, {data: input,headers: {[csrf.headerName]: csrf.token,"Idempotency-Key": requestId}});
}

/** 当前对象的编码规则候选，仅查询指定字段，不消耗序号。 */
export function listCodingOptions(objectType: string, field: string) {
  return http.request<{ encoderKey: string; name: string }[]>(
    "get", `/api/business-objects/${encodeURIComponent(objectType)}/coding-options`, { params: { field } }
  );
}

/** 使用用户主动选定的编码器发号，失败重试由调用方保留同一 UUID。 */
export function issueObjectCode(objectType: string, key: string, requestId: string) {
  return executeObjectCommand<{ code: string }>(
    `/api/business-objects/${encodeURIComponent(objectType)}/encoders/${encodeURIComponent(key)}/issue`, {}, requestId
  );
}
