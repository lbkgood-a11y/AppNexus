import { http } from "@/utils/http";
/** 个人展示资料；不包含密码、Token 或可由客户端修改的权限。 */
export interface AccountProfile { id: string; username: string; displayName: string }
/** 查询当前账号的资料。 */
export const getProfile = () => http.request<AccountProfile>("get", "/bff/account");
/**
 * 为状态修改请求获取当前会话 CSRF 凭据，避免复用登录前或已失效的凭据。
 * @param method 只允许 PATCH/POST；不得通过 GET 修改账号资料
 * @param path 固定的同源个人中心端点
 * @param data 请求数据，仅在当前请求内使用，不写入浏览器存储
 */
async function mutate(method: "patch" | "post", path: string, data: object) {
  const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
  await http.request(method, path, { data, headers: { [csrf.headerName]: csrf.token } });
}
/** 显示名称允许 1 至 100 个字符；服务端进行最终校验。 */
export const saveProfile = (displayName: string) => mutate("patch", "/bff/account", { displayName });
/** 当前密码重新认证后改密；成功会注销所有旧会话，密码不落入持久存储。 */
export const changePassword = (currentPassword: string, newPassword: string) =>
  mutate("post", "/bff/account/password", { currentPassword, newPassword });

/** 本人可见的最小安全事件，不包含凭据、IP 原文或请求正文。 */
export interface SecurityLogEntry {
  id: number;
  action: "LOGIN" | "LOGOUT" | "PROFILE_UPDATED" | "PASSWORD_CHANGED" | "OTHER_SESSIONS_REVOKED";
  occurredAt: string;
}
/**
 * 分页查询当前主体的安全日志，不提供账号参数。
 * @param page 从 1 开始的页码
 * @param size 每页条数，默认 10，服务端最大 50
 */
export const getSecurityLogs = (page = 1, size = 10) =>
  http.request<{ items: SecurityLogEntry[]; total: number }>("get", "/bff/account/security-logs", { params: { page, size } });

/** 在线会话展示模型；handle 为摘要管理标识，不是 Cookie 会话凭据。 */
export interface AccountSession {
  handle: string; createdAt: string; lastAccessedAt: string; current: boolean;
}
/** 仅查询当前认证账号的在线会话，不接受账号 ID。 */
export const getAccountSessions = () => http.request<AccountSession[]>("get", "/bff/account/sessions");
/** CSRF 保护的本人其他会话撤销；当前会话使用标准注销入口。 */
export async function revokeAccountSession(handle: string) {
  const csrf = await http.request<{token: string; headerName: string}>("get", "/bff/csrf");
  await http.request("delete", "/bff/account/sessions/" + encodeURIComponent(handle),
    { headers: { [csrf.headerName]: csrf.token } });
}

/** 保留当前会话，撤销服务端查询快照中的其他会话；需要当前会话 CSRF。 */
export async function revokeOtherSessions() {
  const csrf = await http.request<{token: string; headerName: string}>("get", "/bff/csrf");
  return http.request<{revoked: number}>("post", "/bff/account/sessions/revoke-others",
    { headers: { [csrf.headerName]: csrf.token } });
}
