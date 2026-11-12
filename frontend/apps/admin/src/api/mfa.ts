import { http } from "@/utils/http";

/** 本人第二因子状态；不包含密钥或恢复码。 */
export interface MfaStatus { available: boolean; enabled: boolean; steppedUp: boolean; platformRequired:boolean; methods:("TOTP"|"RECOVERY_CODE")[] }
/** 只查询本人绑定状态，不接受目标账号参数。 */
export const getMfaStatus = () => http.request<MfaStatus>("get", "/bff/account/mfa");
/**
 * 当前密码与一次性因子只通过同源 CSRF 请求传输，不进入持久存储。
 * @param action 已实现的本人认证操作
 * @param password 当前密码，服务端按凭据权威源复核
 * @param code TOTP 或恢复码；初次请求绑定时缺省为空
 */
export async function challengeMfa<T>(action: "enrollment" | "activation" | "step-up" | "disable", password: string, code = "") {
  const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
  return http.request<T>("post", `/bff/account/mfa/${action}`, {
    data: { password, code }, headers: { [csrf.headerName]: csrf.token }
  });
}
