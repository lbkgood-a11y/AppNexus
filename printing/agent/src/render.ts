import type { Browser } from "playwright-core";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";

const require = createRequire(import.meta.url);

/** 平台已领取任务；模板版本和授权快照在服务端固定，不包含人员会话。 */
export interface ClaimedJob {
  id: string;
  copies: number;
  data_snapshot: Record<string, unknown>;
  template_json: Record<string, unknown>;
  system_name: string;
  /** PDF 仅上传产物，不调用队列；旧任务缺省按 PRINTER 处理。 */
  output_type?: "PDF" | "PRINTER";
}

/**
 * 在独立、无网络上下文渲染 PDF；任务结束后清理 Cookie、存储及页面。
 * @param browser 代理专用 Chromium，禁止复用业务浏览器或持久用户目录。
 * @param job 平台返回的固定模板与授权快照；不允许传入自定义渲染 URL。
 * @param file 当前任务私有临时目录下的 PDF 绝对路径。
 * @param timeoutMs 整次渲染期限，默认 60000 毫秒；须小于平台 120 秒领取租约。
 * @throws Error 初始化、渲染或超时失败；尚未进入系统队列，可确定为 FAILED。
 */
export async function renderPdf(browser: Browser, job: ClaimedJob, file: string, timeoutMs = 60_000): Promise<void> {
  const context = await browser.newContext({ javaScriptEnabled: true, serviceWorkers: "block", acceptDownloads: false });
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    // 即使发布校验遗漏资源地址，模板和数据也不能访问站点内网或互联网。
    await context.route("**/*", route => route.abort());
    const render = async () => {
      const page = await context.newPage();
      page.setDefaultTimeout(30_000);
      await page.setContent("<!doctype html><meta charset=utf-8><main id=output></main>");
      await page.addScriptTag({ content: `
        Object.defineProperty(window,'autoConnect',{configurable:false,get:()=>false,set:()=>{}});
        const disabled=()=>{throw new Error('PRINT_CAPABILITY_DISABLED')};
        window.io={io:disabled};window.jspdf={jsPDF:disabled};window.canvg={};
        const values=new Map();
        Object.defineProperty(window,'localStorage',{value:{
          getItem:key=>values.get(key)||null,setItem:(key,value)=>values.set(key,String(value)),
          removeItem:key=>values.delete(key),clear:()=>values.clear()
        }});
      ` });
      await page.addStyleTag({ path: require.resolve("vue-plugin-hiprint/dist/print-lock.css") });
      const assets = [
        require.resolve("jquery/dist/jquery.min.js"),
        require.resolve("jsbarcode/dist/JsBarcode.all.min.js"),
        join(dirname(require.resolve("bwip-js")), "bwip-js-min.js"),
        require.resolve("vue-plugin-hiprint/dist/vue-plugin-hiprint.js")
      ];
      for (const asset of assets) {
        // 二维码库浏览器全局名与 HiPrint UMD 的依赖键不同，必须在加载引擎前连接。
        if(asset===assets[assets.length-1])await page.evaluate(()=>{
          const scope=globalThis as typeof globalThis&Record<string,unknown>;scope["bwip-js"]=scope.bwipjs;
        });
        await page.addScriptTag({ path: asset });
      }
      await page.evaluate(({ template, data }) => {
        type Library = {
          disAutoConnect(): void;
          defaultElementTypeProvider: new () => unknown;
          hiprint: { init(value: unknown): void; PrintTemplate: new (value: { template: unknown }) => { getHtml(data: unknown): unknown } };
        };
        const scope = globalThis as typeof globalThis & Record<string, unknown>;
        const library = scope["vue-plugin-hiprint"] as Library;
        library.disAutoConnect();
        library.hiprint.init({ providers: [new library.defaultElementTypeProvider()], lang: "cn" });
        const instance = new library.hiprint.PrintTemplate({ template });
        const jq = scope.$ as (value: Element) => { empty(): { append(content: unknown): void } };
        jq(document.querySelector("#output")!).empty().append(instance.getHtml(data));
      }, { template: job.template_json, data: job.data_snapshot });
      await page.emulateMedia({ media: "print" });
      await page.evaluate(() => document.fonts.ready.then(() => undefined));
      await page.pdf({ path: file, printBackground: true, preferCSSPageSize: true, tagged: true });
    };
    await Promise.race([
      render(),
      new Promise<never>((_, reject) => { timer = setTimeout(() => reject(new Error("PRINT_RENDER_TIMEOUT")), timeoutMs); })
    ]);
  } finally {
    if (timer) clearTimeout(timer);
    // 关闭上下文会中止超时后仍在执行的页面脚本，不能只返回超时而遗留工作进程。
    await context.close();
  }
}
