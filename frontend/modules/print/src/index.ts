import { frameBootstrap } from "./frame";
import { copyTemplate, type PrintTemplate } from "./template";

export { createBlankTemplate, copyTemplate, type PrintTemplate } from "./template";

/** 接入期仅支持中文与英文；缺省中文，其他语言需先完成平台文案和上游翻译验证。 */
export interface DesignerOptions {
  /** 应用内受信任的原生 JSON 模板；不接受终端用户上传的任意模板。 */
  template: PrintTemplate;
  /** 上游语言代码，默认 cn；不创建独立平台语言状态。 */
  language?: "cn" | "en";
}

/** 隔离的设计会话；不暴露原始引擎、客户端连接、脚本执行或静默打印接口。 */
export interface PrintDesigner {
  /** 获取经过基础检查的模板副本；本方法不负责服务端保存和发布。 */
  snapshot(): Promise<PrintTemplate>;
  /** 使用已授权数据在隔离文档预览；不是打印完成回执或 PDF 导出。 */
  preview(data: Record<string, unknown>): Promise<void>;
  /** 从预览返回设计画布。 */
  design(): Promise<void>;
  /** 删除 iframe、清除事件和待处理请求；可重复调用，销毁后不可继续操作。 */
  destroy(): void;
}

/**
 * 按需加载固定版本 HiPrint，在独立 iframe 中创建设计会话。
 * @param container 已连接文档且由调用方专用的容器，函数只增删自身 iframe。
 * @param options 模板与语言；加载及每个命令超时为 15 秒，不自动重试有副作用操作。
 * @returns 可预览与读取模板的会话；离开页面时必须调用 destroy。
 * @throws Error 模板不符合接入约束、容器无效、资源加载失败或调用超时。
 */
export async function createPrintDesigner(container: HTMLElement, options: DesignerOptions): Promise<PrintDesigner> {
  const template = copyTemplate(options.template);
  if (!container.isConnected) throw new Error("PRINT_CONTAINER_DETACHED");
  // ?url 将上游 UMD 作为本地静态资产提供，不在应用主窗口求值，也不依赖 CDN。
  const [{ default: engineUrl }, { default: cssUrl }, { default: jqueryUrl }, { default: barcodeUrl }, { default: bwipUrl }] = await Promise.all([
    import("vue-plugin-hiprint/dist/vue-plugin-hiprint.js?url"),
    import("vue-plugin-hiprint/dist/print-lock.css?url"),
    import("jquery/dist/jquery.min.js?url"),
    import("jsbarcode/dist/JsBarcode.all.min.js?url"),
    import("../node_modules/bwip-js/dist/bwip-js-min.js?url"),
  ]);
  // 由主窗口读取同站资产再转为 data URL，避免 opaque iframe 的本地网络访问限制。
  const readAsset = async (url: string, type: string): Promise<string> => {
    const response = await fetch(url, { credentials: "omit", signal: AbortSignal.timeout(15_000) });
    if (!response.ok) throw new Error("PRINT_ENGINE_LOAD_FAILED");
    const bytes = new Uint8Array(await response.arrayBuffer());
    let binary = "";
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return `data:${type};base64,${btoa(binary)}`;
  };
  const [engine, css, jquery, barcode, bwip] = await Promise.all([
    readAsset(engineUrl, "text/javascript"), readAsset(cssUrl, "text/css"),
    readAsset(jqueryUrl, "text/javascript"), readAsset(barcodeUrl, "text/javascript"), readAsset(bwipUrl, "text/javascript"),
  ]);
  const channel = crypto.randomUUID();
  const frame = document.createElement("iframe");
  frame.title = options.language === "en" ? "Print designer" : "打印设计器";
  frame.setAttribute("sandbox", "allow-scripts");
  frame.setAttribute("referrerpolicy", "no-referrer");
  frame.style.cssText = "width:100%;height:100%;border:0;display:block";
  const pending = new Map<string, { resolve: (value: any) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  let destroyed = false;
  let readyResolve: () => void;
  let readyReject: (error: Error) => void;
  const ready = new Promise<void>((resolve, reject) => { readyResolve = resolve; readyReject = reject; });
  const readyTimer = setTimeout(() => readyReject(new Error("PRINT_ENGINE_TIMEOUT")), 15_000);
  const receive = (event: MessageEvent) => {
    // sandbox 为 opaque origin，不能靠 origin 授权；只接受指定窗口和随机会话标识。
    if (event.source !== frame.contentWindow || event.data?.channel !== channel) return;
    if (event.data.ready) readyResolve();
    else if (!event.data.id && event.data.error) readyReject(new Error("PRINT_ENGINE_LOAD_FAILED"));
    const request = pending.get(event.data.id);
    if (!request) return;
    clearTimeout(request.timer);
    pending.delete(event.data.id);
    if (event.data.error) request.reject(new Error("PRINT_ENGINE_FAILURE"));
    else request.resolve(event.data.value);
  };
  window.addEventListener("message", receive);
  const destroy = () => {
    if (destroyed) return;
    destroyed = true;
    clearTimeout(readyTimer);
    window.removeEventListener("message", receive);
    frame.remove();
    for (const request of pending.values()) {
      clearTimeout(request.timer);
      request.reject(new Error("PRINT_DESIGNER_DESTROYED"));
    }
    pending.clear();
  };
  const command = (action: string, payload?: unknown): Promise<any> => {
    if (destroyed) return Promise.reject(new Error("PRINT_DESIGNER_DESTROYED"));
    const id = crypto.randomUUID();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { pending.delete(id); reject(new Error("PRINT_ENGINE_TIMEOUT")); }, 15_000);
      pending.set(id, { resolve, reject, timer });
      try { frame.contentWindow!.postMessage({ channel, id, action, payload }, "*"); }
      catch { clearTimeout(timer); pending.delete(id); reject(new Error("PRINT_DATA_NOT_SERIALIZABLE")); }
    });
  };
  const escape = (value: string) => value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;");
  // 上游依赖 eval，暂仅允许此隔离文档执行；不放宽主应用 CSP。网络连接与外部图片默认禁止。
  const policy = `default-src 'none'; script-src ${engine.startsWith("data:") ? "data:" : new URL(engine).origin} 'unsafe-inline' 'unsafe-eval'; style-src ${css.startsWith("data:") ? "data:" : new URL(css).origin} 'unsafe-inline'; img-src data: blob:; connect-src 'none'; font-src 'none'; base-uri 'none'; form-action 'none'`;
  frame.srcdoc = `<!doctype html><html data-channel="${channel}" data-engine="${escape(engine)}"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${escape(policy)}"><link rel="stylesheet" href="${escape(css)}"><style>body{margin:0;overflow:auto}#designer,#preview{min-height:100vh}</style></head><body><main id="designer"></main><main id="preview" hidden></main><script src="${jquery}"></script><script src="${barcode}"></script><script src="${bwip}"></script><script>(${frameBootstrap.toString()})();</script></body></html>`;
  container.append(frame);
  try {
    await ready;
    clearTimeout(readyTimer);
    await command("initialize", { template, language: options.language ?? "cn" });
  } catch (error) { destroy(); throw error; }
  return {
    snapshot: async () => copyTemplate(await command("snapshot")),
    preview: async data => { await command("preview", data); },
    design: async () => { await command("design"); },
    destroy,
  };
}
