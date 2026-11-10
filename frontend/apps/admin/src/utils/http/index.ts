import Axios, { type AxiosRequestConfig } from "axios";
import { i18n } from "@/plugins/i18n";
import type { RequestMethods, PureHttpRequestConfig } from "./types";
import {currentTenant,beginTenantWrite,reloadTenant} from "@/utils/tenantContext";
const client = Axios.create({ timeout: 10000, withCredentials: true });
/**
 * 提取安全的请求错误文案；不输出请求正文、密码、Token 或服务端堆栈。
 * @param error Axios 请求异常或其他未知错误
 */
export function requestErrorMessage(error: unknown): string {
  const t = i18n.global.t as (key: string) => string;
  if (Axios.isAxiosError(error)) {
    const status = error.response?.status;
    const securityCode=error.response?.data?.code;
    if(typeof securityCode==='string'&&["MFA_STEP_UP_REQUIRED","MFA_ENROLLMENT_REQUIRED","MFA_CHALLENGE_INVALID","MFA_UNAVAILABLE","PASSWORD_RECOVERY_INVALID"].includes(securityCode))return t("account.errors."+securityCode);
    if (status === 401) return t("account.sessionExpired");
    if (status === 403) return t("account.forbidden");
    if (status === 429) return t("account.rateLimited");
    const code=error.response?.data?.code;
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("workflow.codes."+code))return t("workflow.codes."+code);
    if(typeof code==='string'&&["PLATFORM_DENIED","DIRECTORY_CONFLICT","INVALID_DIRECTORY_INPUT","TENANT_CONTEXT_CHANGED","TENANT_UNAVAILABLE","TENANT_REQUIRED"].includes(code))return t("administration.errors."+code);
    if(typeof code==='string'&&["MENU_CHANGED","MENU_CODE_CONFLICT","MENU_CONFLICT","INVALID_MENU_INPUT"].includes(code))return t("menuManagement.errors."+code);
    if(typeof code==='string'&&(code.startsWith("ROLE_")||code==="INVALID_ROLE_INPUT"))return t("roleManagement.errors."+code);
    if(typeof code==='string'&&(code.startsWith("DICTIONARY_")||code==="INVALID_DICTIONARY_INPUT"))return t("dictionary.errors."+code);
    if(typeof code==='string'&&code.startsWith("ORGANIZATION_"))return typeof error.response?.data?.detail==="string"?error.response.data.detail:t("account.requestFailed");
    if (!error.response || (status && status >= 500)) return t("account.unavailable");
    const detail = error.response?.data?.detail;
    if (typeof detail === "string" && detail.length <= 300) return detail;
  }
  return t("account.requestFailed");
}
class PureHttp {
  async request<T>(method: RequestMethods, url: string, param?: AxiosRequestConfig, config?: PureHttpRequestConfig): Promise<T> {
    const request = { method, url, ...param, ...config };
    const tenant=currentTenant.value;
    const business=url.startsWith("/api/");
    if(business&&tenant)request.headers={...request.headers,"X-Tenant-Context-Version":String(tenant.version)};
    const finish=business&&!['get','head','options'].includes(method)?beginTenantWrite():undefined;
    config?.beforeRequestCallback?.(request);
    let response;
    try { response = await client.request(request); }
    catch (error) {
      if(business&&Axios.isAxiosError(error)&&error.response?.data?.code==='TENANT_CONTEXT_CHANGED')reloadTenant();
      // 首次会话探测由路由守卫决定去向；后台其他 401 清理全部展示状态。
      if (Axios.isAxiosError(error) && error.response?.status === 401 &&
          (url.startsWith("/bff/") || url.startsWith("/api/")) && url !== "/bff/session") {
        await (await import("@/utils/sessionLifecycle")).clearSession("expired");
      }
      throw error;
    } finally{finish?.();}
    // 切换后旧响应不能覆盖新租户页面；请求头不决定服务器租户来源。
    if(business&&tenant&&currentTenant.value?.version!==tenant.version)throw new Axios.CanceledError("Stale tenant response");
    config?.beforeResponseCallback?.(response);
    // 管理写入后立即复核按钮能力；不能把成功前的授权快照继续用于撤权后的交互。
    if(!['get','head','options'].includes(method)&&(url.startsWith('/bff/platform/')||url.startsWith('/api/access/')||url.startsWith('/api/organization/grants')))
      window.dispatchEvent(new Event('appnexus:authority-changed'));
    return response.data;
  }
  post<T, P>(url: string, params?: AxiosRequestConfig<P>, config?: PureHttpRequestConfig) { return this.request<T>("post", url, params, config); }
  get<T, P>(url: string, params?: AxiosRequestConfig<P>, config?: PureHttpRequestConfig) { return this.request<T>("get", url, params, config); }
}
export const http = new PureHttp();
