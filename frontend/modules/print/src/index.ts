import { frameBootstrap } from "./frame";
import { copyTemplate, type PrintTemplate } from "./template";

export { createBlankTemplate, copyTemplate, type PrintTemplate } from "./template";
export { type DesignContract, type DesignField } from "./authoring";

/** 打印管理 API 数据行；JSON 字段由服务端校验，领域页面不推断业务字段权限。 */
export interface PrintRow { id: string; [key: string]: unknown }
/** 模板草稿写入参数；revision=-1 表示新建。 */
export interface TemplateDraft { code:string; name:string; category:string; dataContract:string; scope:Record<string,unknown>; template:PrintTemplate; revision:number }
/** 设备写入参数；系统队列名称不等于客户端可提交的任意命令。 */
export interface DeviceDraft { code:string; name:string; siteCode:string; systemName:string; capabilities:Record<string,unknown>; enabled:boolean }
/** 应用层 HTTP 适配接口；租户、身份、CSRF 和幂等键由应用统一注入。 */
export interface PrintApi {
  /** 统一输出：PREVIEW 仅生成 PDF，确认接口才会提交物理队列。 */
  createOutput(documentId:string,data:{mode:"SILENT"|"PREVIEW";deviceId:string|null;copies:number},requestId:string):Promise<PrintRow>;
  outputStatus(id:string):Promise<PrintRow>;
  outputPdf(id:string):Promise<Blob>;
  confirmOutput(id:string,deviceId:string,requestId:string):Promise<PrintRow>;
  /** 配置场景、手工单据与认证客户端队列；所有读写由宿主注入租户和 CSRF。 */
  scenes():Promise<PrintRow[]>;
  saveScene(id:string|undefined,data:Record<string,unknown>):Promise<PrintRow[]>;
  documents():Promise<PrintRow[]>;
  saveDocument(data:Record<string,unknown>):Promise<PrintRow[]>;
  printDocument(id:string,data:{deviceId:string|null;outputType:"PDF"|"PRINTER";copies:number},requestId:string):Promise<PrintRow>;
  clientQueues():Promise<Record<string,unknown>[]>;
  /** 服务端注册的设计字段与合成预览值，不包含真实业务数据。 */
  designContracts():Promise<import("./authoring").DesignContract[]>;
  /** 创建不可变租户字段契约，调整结构必须使用新的版本编码。 */
  createContract(data:import("./authoring").DesignContract):Promise<import("./authoring").DesignContract[]>;
  capabilities():Promise<string[]>; templates():Promise<PrintRow[]>; template(id:string):Promise<PrintRow>;
  saveTemplate(id:string|undefined,draft:TemplateDraft):Promise<PrintRow>; publish(id:string,revision:number):Promise<PrintRow>; versions(id:string):Promise<PrintRow[]>;
  devices():Promise<PrintRow[]>; saveDevice(id:string|undefined,draft:DeviceDraft):Promise<PrintRow>; jobs():Promise<PrintRow[]>; reprint(id:string,reason:string):Promise<PrintRow>;
  /** 下载当前租户已完成的 PDF；应用适配必须复用会话与错误处理。 */
  downloadPdf(id:string):Promise<Blob>;
  /** 将指定历史版本恢复为草稿，仍需显式发布；revision 为当前草稿锁版本。 */
  restoreVersion(id:string,version:number,revision:number):Promise<PrintRow>;
}

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
  /** 使用经过基础检查的模板副本重建画布；用于平台属性面板，不暴露上游内部对象。 */
  replace(template: PrintTemplate): Promise<void>;
  /** 调用 HiPrint 原生历史记录，不在平台重复实现画布状态。 */
  undo(): Promise<void>;
  /** 重做 HiPrint 原生历史记录。 */
  redo(): Promise<void>;
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
  let themeTimer:ReturnType<typeof setInterval>|undefined;
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
    if(themeTimer)clearInterval(themeTimer);
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
  // 隔离文档继承平台语义主题变量；纸张始终为白色以表达实际打印输出，不随深色主题反转。
  const theme=getComputedStyle(container);
  const token=(name:string,fallback:string)=>theme.getPropertyValue(name).trim()||fallback;
  const siteFont=theme.fontFamily.replace(/[<>]/g,"");
  const designerTheme=`:root{--site-font:${siteFont};--surface:${token("--el-bg-color","#fff")};--stage:${token("--el-fill-color-light","#f5f7fa")};--text:${token("--el-text-color-primary","#303133")};--muted:${token("--el-text-color-secondary","#606266")};--border:${token("--el-border-color-lighter","#e4e7ed")};--primary:${token("--el-color-primary","#409eff")};--primary-hover:${token("--el-color-primary-light-3","#79bbff")};--primary-active:${token("--el-color-primary-dark-2","#337ecc")}}`;
  const designerLayout=`body{color:var(--text);background:var(--stage)}.side .prop-tab-items,.side .prop-tab-item,.side .hiprint-option-items,.side .hiprint-option-item,.side .hiprint-option-item-field{background-color:var(--surface);color:var(--text)}.side .prop-tab-item.active,.side .prop-tab-item.selected{color:var(--primary);border-color:var(--primary)}.side label,.side .hiprint-option-item-label{color:var(--muted)}.side input,.side select,.side textarea{background-color:var(--surface)!important;color:var(--text)!important;border-color:var(--border)!important}.side input:focus,.side select:focus,.side textarea:focus{border-color:var(--primary)!important}.hiprint-printElement.selected,.hiprint-printElement:hover{outline-color:var(--primary)}.hiprint-option-item-submitBtn{background:var(--primary)!important;border:1px solid var(--primary)!important;color:#fff!important;border-radius:4px;font-family:var(--site-font);padding:6px 14px;cursor:pointer}.hiprint-option-item-submitBtn:hover{background:var(--primary-hover)!important;border-color:var(--primary-hover)!important}.hiprint-option-item-submitBtn:active{background:var(--primary-active)!important;border-color:var(--primary-active)!important}.hiprint-option-item-submitBtn:focus-visible{outline:2px solid var(--primary);outline-offset:2px}html,body{height:100%;overflow:hidden}.workspace{height:100vh;min-height:0;grid-template-columns:240px minmax(0,1fr) 336px;background:var(--stage)}.side{font-family:var(--site-font);font-size:14px;line-height:1.6;height:100vh;overflow:auto;padding:16px;background:var(--surface);color:var(--text)}.side h2{position:sticky;top:-16px;z-index:2;margin:-16px -16px 16px;padding:16px;font-size:14px;font-weight:600;border-bottom:1px solid var(--border);background:var(--surface)}.left,.right{border-color:var(--border)}.right{grid-column:auto;border-top:0;border-left:1px solid var(--border)}.stage{min-width:0;height:100vh;overflow:auto;padding:32px 40px;background-image:radial-gradient(var(--border) .7px,transparent .7px);background-size:16px 16px}#designer,#preview{width:max-content;min-width:100%;min-height:100%;display:flex;justify-content:center;align-items:flex-start}.hiprint-printTemplate{flex:none;margin:0 auto!important}#preview[hidden],#designer[hidden]{display:none}.hiprint-printPaper{background:#fff;color:#000;box-shadow:0 1px 8px #00000016}.hiprint-printElement-type .title{font-size:14px;color:var(--muted);margin:16px 0 8px}.hiprint-printElement-type ul{gap:8px}.ep-draggable-item{min-height:36px;display:flex;align-items:center;justify-content:center;padding:8px 4px;border-color:var(--border);background:var(--surface);color:var(--text);font-family:var(--site-font);font-size:14px;transition:border-color .15s,background .15s}.ep-draggable-item:hover{color:var(--primary);border-color:var(--primary);background:var(--stage)}.hiprint-option-item-row{padding:0 0 12px;margin:0 0 12px;border-bottom:1px solid var(--border)}.hiprint-option-item-label{font-size:14px;color:var(--muted);line-height:1.6;padding-bottom:6px}.hiprint-option-item{min-width:0}.hiprint-option-item input,.hiprint-option-item select{max-width:100%;box-sizing:border-box}.hiprint-option-item-row{display:flow-root}.hiprint-option-item input,.hiprint-option-item select,.hiprint-option-item textarea{background:var(--surface);color:var(--text);border-color:var(--border);min-height:32px;font:inherit;outline:none}.hiprint-option-item input:focus,.hiprint-option-item select:focus{border-color:var(--primary)}.prop-tab-items{border-color:var(--border)}.prop-tab-item{font-size:12px;color:var(--muted)}@media(max-width:1000px){.workspace{grid-template-columns:160px minmax(0,1fr) 240px}.side{padding:12px}.stage{padding:20px}}@media(max-width:700px){.workspace{grid-template-columns:140px minmax(360px,1fr) 220px;width:max(100vw,720px)}.right{grid-column:auto;border-top:0;border-left:1px solid var(--border)}body{overflow-x:auto}}`;
  frame.srcdoc = `<!doctype html><html data-channel="${channel}" data-engine="${escape(engine)}"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${escape(policy)}"><link rel="stylesheet" href="${escape(css)}"><style>:root{color-scheme:light;font:14px Arial,'Microsoft YaHei',sans-serif}*{box-sizing:border-box}body{margin:0;color:#303133;background:#f5f7fa}.workspace{display:grid;min-height:100vh;grid-template-columns:220px minmax(600px,1fr) 300px}.side{min-width:0;padding:14px;background:#fff}.left{border-right:1px solid #e4e7ed}.right{border-left:1px solid #e4e7ed}.side h2{margin:0 0 12px;font-size:15px}.stage{overflow:auto;padding:24px}.hiprint-printTemplate{margin:auto}#designer,#preview{min-height:calc(100vh - 48px)}#pagination{margin-top:12px}.hiprint-printElement-type{margin:0;padding:0;list-style:none}.hiprint-printElement-type>li{margin-bottom:14px}.hiprint-printElement-type .title{display:block;margin-bottom:6px;color:#606266;font-weight:600}.hiprint-printElement-type ul{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:0;padding:0;list-style:none}.ep-draggable-item{display:block;padding:8px;border:1px solid #dcdfe6;border-radius:4px;color:#303133;text-align:center;background:#fff;cursor:grab}.ep-draggable-item:hover{border-color:#409eff;color:#409eff}.hiprint-option-items{padding-bottom:12px}.hiprint-option-item-row{margin-bottom:10px}.hiprint-option-item-label{margin-bottom:4px;color:#606266}.hiprint-option-item input,.hiprint-option-item select,.hiprint-option-item textarea{max-width:100%;border:1px solid #dcdfe6;border-radius:4px;padding:6px}.prop-tab-items{display:flex;gap:4px;margin:0 0 12px;padding:0;border-bottom:1px solid #e4e7ed;list-style:none}.prop-tab-item{padding:8px 4px;cursor:pointer}@media(max-width:900px){.workspace{grid-template-columns:180px minmax(520px,1fr)}.right{grid-column:1/-1;border-top:1px solid #e4e7ed;border-left:0}}${designerTheme}${designerLayout}</style></head><body><div class="workspace"><aside class="side left"><h2>${options.language === "en" ? "Elements" : "打印元素"}</h2><div id="elements"></div></aside><main class="stage"><div id="designer"></div><div id="preview" hidden></div><div id="pagination"></div></main><aside class="side right"><h2>${options.language === "en" ? "Properties" : "元素属性"}</h2><div id="settings"></div></aside></div><script src="${jquery}"></script><script src="${barcode}"></script><script src="${bwip}"></script><script>(${frameBootstrap.toString()})();</script></body></html>`;
  container.append(frame);
  try {
    await ready;
    clearTimeout(readyTimer);
    await command("initialize", { template, language: options.language ?? "cn" });
    // 隔离 iframe 无法继承宿主 CSS；读取实时计算值，只在主题变化时同步，不重建设计或清空历史。
    let previousTheme="";
    const syncTheme=()=>{
      if(destroyed)return;
      const current=getComputedStyle(container);
      const names:Record<string,string>={"--surface":"--el-bg-color","--stage":"--el-fill-color-light","--text":"--el-text-color-primary","--muted":"--el-text-color-secondary","--border":"--el-border-color-lighter","--primary":"--el-color-primary","--primary-hover":"--el-color-primary-light-3","--primary-active":"--el-color-primary-dark-2"};
      const values:Record<string,string|boolean>={"--site-font":current.fontFamily,dark:document.documentElement.classList.contains("dark")};
      for(const [target,source] of Object.entries(names))values[target]=current.getPropertyValue(source).trim();
      const serialized=JSON.stringify(values);
      if(serialized!==previousTheme){previousTheme=serialized;void command("theme",values).catch(()=>{previousTheme=""})}
    };
    syncTheme();themeTimer=setInterval(syncTheme,500);
  } catch (error) { destroy(); throw error; }
  return {
    snapshot: async () => copyTemplate(await command("snapshot")),
    replace: async value => { await command("replace", copyTemplate(value)); },
    undo: async () => { await command("undo"); },
    redo: async () => { await command("redo"); },
    preview: async data => { await command("preview", data); },
    design: async () => { await command("design"); },
    destroy,
  };
}
