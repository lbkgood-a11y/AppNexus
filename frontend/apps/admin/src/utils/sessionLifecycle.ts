import { removeToken } from "@/utils/auth";
/** 会话结束原因，用于登录页提供精确反馈，不包含身份凭据。 */
export type SessionEndReason = "expired" | "passwordChanged";
let pending: Promise<void> | undefined;
/**
 * 清除应用内会话展示、菜单与标签状态；重复的并发 401 共用一次清理过程。
 * @param reason expired 表示会话失效，passwordChanged 表示改密后必须重新认证
 */
export function clearSession(reason: SessionEndReason): Promise<void> {
  if (pending) return pending;
  pending = (async () => {
    removeToken();
    const context=await import("@/utils/tenantContext");context.currentTenant.value=undefined;context.availableTenants.value=[];
    const [{ router, resetRouter }, { useUserStoreHook }, { useMultiTagsStoreHook }, { routerArrays }] =
      await Promise.all([import("@/router"), import("@/store/modules/user"),
        import("@/store/modules/multiTags"), import("@/layout/types")]);
    useUserStoreHook().$reset();
    useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
    resetRouter();
    await router.replace({ path: "/login", query: { reason } });
  })().finally(() => { pending = undefined; });
  return pending;
}


/**
 * 后台每 60 秒及窗口重新获得焦点时复核会话，仅在已登录页面执行。
 * 401 才清理会话，服务不可用保留用户页面；同一时刻只允许一次检查。
 * @returns 卸载时释放定时器和事件监听的清理函数
 */
export function monitorSession(): () => void {
  let running = false;
  let stopped = false;
  // 最近 60 秒没有真实交互时停止轮询，避免后台探测持续延长服务端空闲会话。
  let lastActivity = Date.now();
  const activity = () => { lastActivity = Date.now(); };
  async function check() {
    if (running || stopped || document.hidden) return;
    const { router } = await import("@/router");
    if (["/login", "/server-error"].includes(router.currentRoute.value.path)) return;
    running = true;
    try { await (await import("@/api/session")).restoreSession(); await (await import("@/router/navigation")).refreshNavigation(); }
    catch (error) {
      const { isAxiosError } = await import("axios");
      if (isAxiosError(error) && error.response?.status === 401) await clearSession("expired");
    } finally { running = false; }
  }
  const timer = window.setInterval(() => {
    if (Date.now() - lastActivity < 60_000) void check();
  }, 60_000);
  window.addEventListener("pointerdown", activity, { passive: true });
  window.addEventListener("keydown", activity);
  window.addEventListener("focus", check);
  document.addEventListener("visibilitychange", check);
  return () => {
    stopped = true; window.clearInterval(timer);
    window.removeEventListener("pointerdown", activity);
    window.removeEventListener("keydown", activity);
    window.removeEventListener("focus", check);
    document.removeEventListener("visibilitychange", check);
  };
}
