/**
 * iframe 内执行的桥接入口；必须保持自包含，运行在无同源权限的 sandbox 内。
 * 上游直接修改 window、document 和 jQuery，独立文档可避免污染应用壳和遗留全局事件。
 */
export function frameBootstrap(): void {
  const scope = window as unknown as Record<string, any>;
  // bwip-js 浏览器包导出 bwipjs，而 HiPrint UMD 依赖键为 bwip-js；须在引擎求值前适配。
  scope["bwip-js"]=scope.bwipjs;
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
  let history:string[]=[],future:string[]=[];
  /** 统一记录原生拖拽与平台字段/纸张操作；最多 30 份快照，禁止 replace 重建后清空撤销。 */
  const remember=()=>{
    if(!template)return;
    const value=JSON.stringify(template.getJson());
    if(history[history.length-1]!==value){history.push(value);if(history.length>30)history.shift();future=[]}
  };
  const rebuild=(value:any)=>{
    scope.$("#designer").empty();scope.$("#settings").empty();scope.$("#pagination").empty();
    template=new scope["vue-plugin-hiprint"].hiprint.PrintTemplate({template:value,history:true,settingContainer:"#settings",paginationContainer:"#pagination"});
    template.design("#designer");
    document.getElementById("designer")!.hidden=false;document.getElementById("preview")!.hidden=true;
  };
  setInterval(remember,500);
  window.addEventListener("message", event => {
    if (event.source !== parent || event.data?.channel !== channel) return;
    const { id, action, payload } = event.data;
    try {
      const library = scope["vue-plugin-hiprint"];
      if(action==="theme"){
        // 仅接受固定语义变量，禁止父消息借主题同步写入任意样式或改变模板内容。
        for(const name of ["--surface","--stage","--text","--muted","--border","--primary","--primary-hover","--primary-active","--site-font"]){
          if(typeof payload?.[name]==="string")document.documentElement.style.setProperty(name,payload[name]);
        }
        document.documentElement.style.colorScheme=payload?.dark?"dark":"light";
      } else if (action === "initialize") {
        library.disAutoConnect();
        library.hiprint.init({ providers: [new library.defaultElementTypeProvider()], lang: payload.language });
        // 默认提供器注册在 defaultModule；省略模块名会读取空的 _default，导致元素栏为空。
        library.hiprint.PrintElementTypeManager.build("#elements", "defaultModule");
        template = new library.hiprint.PrintTemplate({ template: payload.template, history: true,
          settingContainer: "#settings", paginationContainer: "#pagination" });
        template.design("#designer");
        remember();
      } else if (action === "snapshot") {
        send({ id, value: JSON.parse(JSON.stringify(template.getJson())) });
        return;
      } else if (action === "replace") {
        remember();rebuild(payload);remember();
      } else if (action === "undo") {
        remember();if(history.length>1){future.push(history.pop()!);rebuild(JSON.parse(history[history.length-1]));}
      } else if (action === "redo") {
        const current=JSON.stringify(template.getJson());
        if(current!==history[history.length-1])remember();
        if(future.length){const value=future.pop()!;history.push(value);rebuild(JSON.parse(value));}
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
