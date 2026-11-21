import { watch, watchEffect, shallowRef, type Directive } from "vue";
import { http } from "@/utils/http";
import { currentTenant } from "@/utils/tenantContext";

/** 按钮操作能力只控制展示；无快照、切换租户或读取失败时默认隐藏，后端逐请求复核。 */
const snapshot = shallowRef<{ context: string; operations: Set<string> }>();
let pending: Promise<void> | undefined;
let pendingContext: string | undefined;
let pendingGeneration = -1;
let stopContextWatch: (() => void) | undefined;
let consumers = 0;
let timer: ReturnType<typeof setInterval> | undefined;
let generation = 0;
let refreshSeconds = 0;
/**
 * 设置本机权限检查间隔；只影响按钮展示，业务 API 始终由后端鉴权。
 * @param seconds 间隔秒数；0（默认）关闭轮询，启用范围为 30–3600 的整数，非法值回退 0；立即重建共享定时器。
 */
export function setOperationRefreshSeconds(seconds: unknown): void {
  refreshSeconds = typeof seconds === "number" && Number.isInteger(seconds) && seconds >= 30 && seconds <= 3600 ? seconds : 0;
  clearInterval(timer); timer = undefined;
  if (consumers > 0 && refreshSeconds > 0) timer = setInterval(() => { void refresh(); }, refreshSeconds * 1000);
}
const cleanup = new WeakMap<HTMLElement, () => void>();
const values = new WeakMap<HTMLElement, string>();
/** 当前展示域只用于选择受权能力查询，不作为服务端租户来源。 */
function context() {
  const path = location.hash.startsWith("#/") ? location.hash.slice(1) : location.pathname;
  const tenant = currentTenant.value;
  const identity = `${tenant?.tenantId ?? "none"}:${tenant?.memberId ?? "none"}:${tenant?.version ?? 0}`;
  if(path.startsWith("/account")) return `account:${identity}`;
  return path.startsWith("/platform") ? "platform" : `tenant:${identity}`;
}
/** 同一上下文复核期间保留按钮，避免布局闪动；失败默认隐藏，上下文变化立即失效，API 始终复核权限。 */
async function refresh() {
  const expected = context(); const epoch = generation;
  // 仅合并同一世代和上下文的请求；旧页面在途请求不能阻止新页面取得权限。
  if (pending && pendingContext === expected && pendingGeneration === epoch) return pending;
  if (snapshot.value?.context !== expected) snapshot.value = undefined;
  pendingContext = expected; pendingGeneration = epoch;
  const task = (async () => {
    try {
      // 个人中心同时包含本人操作与租户参数；分别读取真实管理域，不把平台权限代入租户。
      const paths = expected.startsWith("account:") ? ["/bff/operation-decisions", ...(currentTenant.value?.tenantId ? ["/api/operation-decisions"] : [])] : [expected === "platform" ? "/bff/operation-decisions" : "/api/operation-decisions"];
      const codes = (await Promise.all(paths.map(path => http.request<string[]>("get", path)))).flat();
      if (generation === epoch && context() === expected) snapshot.value = { context: expected, operations: new Set(codes) };
    } catch { if (generation === epoch && context() === expected) snapshot.value = undefined; }
  })().finally(() => { if (pending === task) pending = undefined; });
  pending = task;
  return pending;
}
/** 注销时先丢弃展示能力，旧账号的在途响应不得恢复另一账号的按钮。 */
function reset() { generation++; snapshot.value = undefined; }
/** 角色操作按页面管理域展开；租户启停真实使用 update，不虚构 enable 权限。 */
function permission(code: string) {
  if (!code.startsWith("@role:")) return code;
  const platform = context() === "platform";
  const action = code.slice(6);
  if(action === "grant") return platform ? "PlatformRole.authorizationReplacePlatform" : "TenantRole.authorizationReplaceTenant";
  const names: Record<string,string> = {create:"create",update:"update",enable:"setStatus",delete:"delete"};
  return names[action] ? `${platform ? "PlatformRole" : "TenantRole"}.${names[action]}` : "";
}
/**
 * 复用模板自定义指令入口，以稳定操作标识绑定实际按钮，不改变 Element Plus 的业务禁用/加载状态。
 * @example v-operation="'Account.revokeSessions'"
 */
export const operation: Directive<HTMLElement, string> = {
  // 异步能力尚未返回前不短暂展示敏感操作，避免首帧出现全部按钮。
  beforeMount(element) { element.style.display = "none"; },
  mounted(element, binding) {
    values.set(element, binding.value); const original = "";
    const stop = watchEffect(() => {
      const current = context();
      const allowed = (values.get(element) ?? "").split("|").some(code => snapshot.value?.operations.has(permission(code)));
      element.style.display = snapshot.value?.context === current && allowed ? original : "none";
    });
    cleanup.set(element, stop);
    if (consumers++ === 0) {
      // 监听稳定身份字段，避免路由守卫重读同一租户时重复请求；版本变化必须重新取证。
      stopContextWatch = watch(() => context(), () => { reset(); void refresh(); }, { flush: "sync" });
      setOperationRefreshSeconds(refreshSeconds); window.addEventListener("focus", refresh); window.addEventListener("hashchange", refresh); window.addEventListener("appnexus:authority-changed", refresh); window.addEventListener("appnexus:session-ended", reset);
    }
    void refresh();
  },
  updated(element, binding) { if (values.get(element) !== binding.value) { values.set(element, binding.value); void refresh(); } },
  unmounted(element) {
    cleanup.get(element)?.(); cleanup.delete(element); values.delete(element);
    if (--consumers === 0) { stopContextWatch?.(); stopContextWatch = undefined; clearInterval(timer); timer = undefined; reset(); window.removeEventListener("focus", refresh); window.removeEventListener("hashchange", refresh); window.removeEventListener("appnexus:authority-changed", refresh); window.removeEventListener("appnexus:session-ended", reset); }
  }
};
