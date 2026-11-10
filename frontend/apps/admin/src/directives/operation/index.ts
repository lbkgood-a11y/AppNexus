import { watchEffect, shallowRef, type Directive } from "vue";
import { http } from "@/utils/http";
import { currentTenant } from "@/utils/tenantContext";

/** 按钮操作能力只控制展示；无快照、切换租户或读取失败时默认隐藏，后端逐请求复核。 */
const snapshot = shallowRef<{ context: string; permissions: Set<string> }>();
let pending: Promise<void> | undefined;
let consumers = 0;
let timer: ReturnType<typeof setInterval> | undefined;
let generation = 0;
const cleanup = new WeakMap<HTMLElement, () => void>();
const values = new WeakMap<HTMLElement, string>();
/** 当前展示域只用于选择受权能力查询，不作为服务端租户来源。 */
function context() {
  const path = location.hash.startsWith("#/") ? location.hash.slice(1) : location.pathname;
  return path.startsWith("/platform") ? "platform" : `tenant:${currentTenant.value?.tenantId ?? "none"}`;
}
/** 主动刷新不沿用旧授权；并发按钮共用一次请求，30 秒及焦点恢复复核，点击前仍由 API 验证。 */
async function refresh() {
  if (pending) return pending;
  const expected = context(); const epoch = generation; snapshot.value = undefined;
  pending = (async () => {
    try {
      const codes = await http.request<string[]>("get", expected === "platform" ? "/bff/operation-capabilities" : "/api/operation-capabilities");
      if (generation === epoch && context() === expected) snapshot.value = { context: expected, permissions: new Set(codes) };
    } catch { snapshot.value = undefined; }
  })().finally(() => { pending = undefined; });
  return pending;
}
/** 注销时先丢弃展示能力，旧账号的在途响应不得恢复另一账号的按钮。 */
function reset() { generation++; snapshot.value = undefined; }
/** 角色操作按页面管理域展开；租户启停真实使用 update，不虚构 enable 权限。 */
function permission(code: string) {
  if (!code.startsWith("@role:")) return code;
  const platform = context() === "platform";
  const action = code.slice(6);
  return `${platform ? "access.role" : "access.tenant.role"}:${!platform && action === "enable" ? "update" : action}`;
}
/**
 * 复用模板自定义指令入口，以稳定权限码绑定实际按钮，不改变 Element Plus 的业务禁用/加载状态。
 * @example v-operation="'identity.account:revoke_sessions'"
 */
export const operation: Directive<HTMLElement, string> = {
  // 异步能力尚未返回前不短暂展示敏感操作，避免首帧出现全部按钮。
  beforeMount(element) { element.style.display = "none"; },
  mounted(element, binding) {
    values.set(element, binding.value); const original = "";
    const stop = watchEffect(() => {
      const current = context();
      const allowed = (values.get(element) ?? "").split("|").some(code => snapshot.value?.permissions.has(permission(code)));
      element.style.display = snapshot.value?.context === current && allowed ? original : "none";
    });
    cleanup.set(element, stop);
    if (consumers++ === 0) { timer = setInterval(() => { void refresh(); }, 30000); window.addEventListener("focus", refresh); window.addEventListener("hashchange", refresh); window.addEventListener("appnexus:authority-changed", refresh); window.addEventListener("appnexus:session-ended", reset); }
    void refresh();
  },
  updated(element, binding) { if (values.get(element) !== binding.value) { values.set(element, binding.value); void refresh(); } },
  unmounted(element) {
    cleanup.get(element)?.(); cleanup.delete(element); values.delete(element);
    if (--consumers === 0) { clearInterval(timer); timer = undefined; reset(); window.removeEventListener("focus", refresh); window.removeEventListener("hashchange", refresh); window.removeEventListener("appnexus:authority-changed", refresh); window.removeEventListener("appnexus:session-ended", reset); }
  }
};
