import Axios, { type AxiosRequestConfig } from "axios";
import { i18n } from "@/plugins/i18n";
import type { RequestMethods, PureHttpRequestConfig } from "./types";
import {currentTenant,beginTenantWrite,reloadTenant} from "@/utils/tenantContext";
/** 仅用于陈旧请求保护的内存选择，不作为服务端授权；租户切换与组件卸载会清空。 */
let personnelSelection:{personId:string;organizationId:string}|undefined;
window.addEventListener("appnexus:person-selected",((event:CustomEvent)=>{personnelSelection=event.detail;}) as EventListener);
const client = Axios.create({ timeout: 10000, withCredentials: true });
/**
 * 提取安全的请求错误文案；不输出请求正文、密码、Token 或服务端堆栈。
 * @param error Axios 请求异常或其他未知错误
 */
export function requestErrorMessage(error: unknown): string {
  const t = i18n.global.t as (key: string) => string;
  const productCode=(error as {response?:{data?:{code?:string}}})?.response?.data?.code;
  if(productCode&&productCode.startsWith("PRODUCT_")){const key="productErrors."+productCode;const message=t(key);if(message!==key)return message;}

  // 主动取消及陈旧租户响应不向用户报告失败；会话失效由生命周期统一提示。
  if (Axios.isCancel(error)) return "";
  if(error instanceof Error&&(i18n.global.te as (key:string)=>boolean)("printing.codes."+error.message))return t("printing.codes."+error.message);
  if (Axios.isAxiosError(error)) {
    const status = error.response?.status;
    const securityCode=error.response?.data?.code;
    if(typeof securityCode==='string'&&["MFA_STEP_UP_REQUIRED","MFA_ENROLLMENT_REQUIRED","MFA_CHALLENGE_INVALID","MFA_UNAVAILABLE","PASSWORD_RECOVERY_INVALID"].includes(securityCode))return t("account.errors."+securityCode);
    if (status === 401) return "";
    if(['PERSON_CONTEXT_REQUIRED','PERSON_CONTEXT_CHANGED','PERSON_CONTEXT_INVALID'].includes(securityCode))return t("personnel.codes.PERSON_CONTEXT_REQUIRED");
    if (status === 403) return t("account.forbidden");
    if (status === 429) return t("account.rateLimited");
    const code=error.response?.data?.code;
    // 契约错误只呈现服务端稳定码与字段路径，不回显提交值；长度限制避免异常响应撑开页面。
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("leavePoc.codes."+code))return t("leavePoc.codes."+code);
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("dataContractErrors."+code)){
      const field=error.response?.data?.field;
      return t("dataContractErrors."+code)+(typeof field==='string'?` (${field.slice(0,120)})`:'');
    }
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("personnel.codes."+code))return t("personnel.codes."+code);
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("masterData.codes."+code))return t("masterData.codes."+code);
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("businessObject.codes."+code))return t("businessObject.codes."+code);
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("printing.codes."+code))return t("printing.codes."+code);
    if(typeof code==='string'&&(i18n.global.te as (key:string)=>boolean)("workflow.codes."+code))return t("workflow.codes."+code);
    if(typeof code==='string'&&["PLATFORM_DENIED","DIRECTORY_CONFLICT","INVALID_DIRECTORY_INPUT","TENANT_CONTEXT_CHANGED","TENANT_UNAVAILABLE","TENANT_REQUIRED"].includes(code))return t("administration.errors."+code);
    if(typeof code==='string'&&["MENU_CHANGED","MENU_CODE_CONFLICT","MENU_CONFLICT","INVALID_MENU_INPUT"].includes(code))return t("menuManagement.errors."+code);
    if(typeof code==='string'&&(code.startsWith("ROLE_")||code==="INVALID_ROLE_INPUT"))return t("roleManagement.errors."+code);
    if(typeof code==='string'&&(code.startsWith("DICTIONARY_")||code==="INVALID_DICTIONARY_INPUT"))return t("dictionary.errors."+code);
    if(typeof code==='string'&&code.startsWith("ORGANIZATION_"))return typeof error.response?.data?.detail==="string"?error.response.data.detail:t("account.requestFailed");
    if (!error.response || (status && status >= 500)) return t("account.unavailable");
    const detail = error.response?.data?.detail;
    if (typeof detail === "string" && (detail.length <= 300 || code === "PERMISSION_SCOPE_MISMATCH")) return detail;
  }
  return t("account.requestFailed");
}
class PureHttp {
  async request<T>(method: RequestMethods, url: string, param?: AxiosRequestConfig, config?: PureHttpRequestConfig): Promise<T> {
    const request = { method, url, ...param, ...config };
    const tenant=currentTenant.value;
    // 不同会话的租户版本可能相同，必须同时比较租户与成员，不能仅比较计数。
    const tenantChanged=()=>currentTenant.value?.version!==tenant?.version||currentTenant.value?.tenantId!==tenant?.tenantId||currentTenant.value?.memberId!==tenant?.memberId;
    const business=url.startsWith("/api/");
    if(business&&tenant)request.headers={...request.headers,"X-Tenant-Context-Version":String(tenant.version)};
    if(business&&personnelSelection)request.headers={...request.headers,"X-Person-Id":personnelSelection.personId,"X-Person-Organization":personnelSelection.organizationId};
    const finish=business&&url!=="/api/master-data/person/identity"&&!['get','head','options'].includes(method)?beginTenantWrite():undefined;
    config?.beforeRequestCallback?.(request);
    let response;
    try { response = await client.request(request); }
    catch (error) {
      // 拒绝响应也属于旧租户结果；不能只拦截成功响应，否则切换后仍会弹出旧业务错误。
      // 401 属于全局会话失效，仍交由下方会话生命周期处理。
      if (business && tenant && tenantChanged() &&
          !(Axios.isAxiosError(error) && error.response?.status === 401)) {
        throw new Axios.CanceledError("Stale tenant error");
      }
      if(business&&Axios.isAxiosError(error)&&error.response?.data?.code==='TENANT_CONTEXT_CHANGED') {
        // 恢复成功后旧请求按取消处理，避免新页面仍显示旧冲突；写入始终不自动重放。
        await reloadTenant();
        throw new Axios.CanceledError("Tenant context recovered");
      }
      // 首次会话探测由路由守卫决定去向；后台其他 401 清理全部展示状态。
      if (Axios.isAxiosError(error) && error.response?.status === 401 &&
          (url.startsWith("/bff/") || url.startsWith("/api/")) && url !== "/bff/session") {
        await (await import("@/utils/sessionLifecycle")).clearSession("expired");
      }
      throw error;
    } finally{finish?.();}
    // 切换后旧响应不能覆盖新租户页面；请求头不决定服务器租户来源。
    if(business&&tenant&&tenantChanged())throw new Axios.CanceledError("Stale tenant response");
    config?.beforeResponseCallback?.(response);
    // 管理写入后立即复核按钮能力；不能把成功前的授权快照继续用于撤权后的交互。
    if(!['get','head','options'].includes(method)&&(url.startsWith('/bff/platform/')||url.startsWith('/api/access/')))
      window.dispatchEvent(new Event('appnexus:authority-changed'));
    return response.data;
  }
  post<T, P>(url: string, params?: AxiosRequestConfig<P>, config?: PureHttpRequestConfig) { return this.request<T>("post", url, params, config); }
  get<T, P>(url: string, params?: AxiosRequestConfig<P>, config?: PureHttpRequestConfig) { return this.request<T>("get", url, params, config); }
}
export const http = new PureHttp();
