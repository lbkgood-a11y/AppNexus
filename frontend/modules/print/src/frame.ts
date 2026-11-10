/**
 * iframe 内执行的桥接入口；必须保持自包含，运行在无同源权限的 sandbox 内。
 * 上游直接修改 window、document 和 jQuery，独立文档可避免污染应用壳和遗留全局事件。
 */
export function frameBootstrap(): void {
  const scope = window as unknown as Record<string, any>;
  const channel = document.documentElement.dataset.channel!;
  const send = (message: Record<string, unknown>) => parent.postMessage({ channel, ...message }, "*");
  // 上游在模块求值时重设 autoConnect 并注册 ready 回调，故在加载前锁定为 false。
  Object.defineProperty(window, "autoConnect", { configurable: false, get: () => false, set: () => {} });
  // UMD 在求值时读取可选依赖；本期明确禁用连接和图片式 PDF，不加载这些旧依赖。
  const disabled = () => { throw new Error("PRINT_CAPABILITY_DISABLED"); };
  scope.io = { io: disabled };
  scope.jspdf = { jsPDF: disabled };
  scope.canvg = {};
  // 上游读取本地展示偏好；opaque 文档无持久存储权限，仅提供会话内存替身。
  const values = new Map<string, string>();
  Object.defineProperty(window, "localStorage", { value: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, String(value)),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear(),
  } });
  let template: any;
  window.addEventListener("message", event => {
    if (event.source !== parent || event.data?.channel !== channel) return;
    const { id, action, payload } = event.data;
    try {
      const library = scope["vue-plugin-hiprint"];
      if (action === "initialize") {
        library.disAutoConnect();
        library.hiprint.init({ providers: [new library.defaultElementTypeProvider()], lang: payload.language });
        library.hiprint.PrintElementTypeManager.build("#elements");
        template = new library.hiprint.PrintTemplate({ template: payload.template, history: true,
          settingContainer: "#settings", paginationContainer: "#pagination" });
        template.design("#designer");
      } else if (action === "snapshot") {
        send({ id, value: JSON.parse(JSON.stringify(template.getJson())) });
        return;
      } else if (action === "replace") {
        scope.$("#designer").empty();
        scope.$("#settings").empty();
        template = new library.hiprint.PrintTemplate({ template: payload, history: true,
          settingContainer: "#settings", paginationContainer: "#pagination" });
        template.design("#designer");
      } else if (action === "undo") {
        template.undo();
      } else if (action === "redo") {
        template.redo();
      } else if (action === "preview") {
        const target = document.getElementById("preview")!;
        scope.$(target).empty().append(template.getHtml(payload));
        document.getElementById("designer")!.hidden = true;
        target.hidden = false;
      } else if (action === "design") {
        document.getElementById("designer")!.hidden = false;
        document.getElementById("preview")!.hidden = true;
      } else throw new Error("PRINT_ACTION_UNSUPPORTED");
      send({ id, value: null });
    } catch {
      // 不跨边界传递上游异常正文，防止模板数据或内部路径进入主应用日志。
      send({ id, error: "PRINT_ENGINE_FAILURE" });
    }
  });
  const script = document.createElement("script");
  script.addEventListener("load", () => send({ ready: true }));
  script.addEventListener("error", () => send({ error: "PRINT_ENGINE_LOAD_FAILED" }));
  script.src = document.documentElement.dataset.engine!;
  document.body.append(script);
}
