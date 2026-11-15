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
  // 搜索仅切换原生注册节点的可见性，不重建节点，避免丢失上游拖拽事件。
  const search = document.getElementById("element-search") as HTMLInputElement | null;
  search?.addEventListener("input", () => {
    const query = search.value.trim().toLocaleLowerCase();
    document.querySelectorAll<HTMLElement>("#elements .ep-draggable-item").forEach(item => {
      item.hidden = !item.textContent?.toLocaleLowerCase().includes(query);
    });
  });
  // 折叠仅改变工作区列宽，纸张坐标与引擎缩放均不参与计算。
  for (const side of ["left", "right"]) {
    document.getElementById(`toggle-${side}`)?.addEventListener("click", event => {
      const collapsed = document.querySelector(".workspace")!.classList.toggle(`${side}-collapsed`);
      (event.currentTarget as HTMLButtonElement).setAttribute("aria-expanded", String(!collapsed));
    });
  }
  // iframe 内仅复用上游挂载点；标签切换不销毁属性控件，保留实时更新约定。
  document.querySelectorAll<HTMLButtonElement>("[data-tab]").forEach(button => {
    button.addEventListener("click", () => {
      const group = button.parentElement!;
      group.querySelectorAll<HTMLButtonElement>("[data-tab]").forEach(peer => {
        const active = peer === button;
        peer.setAttribute("aria-selected", String(active));
        document.getElementById(`resource-${peer.dataset.tab}`)!.hidden = !active;
      });
    });
  });
  const stage = document.querySelector<HTMLElement>(".stage");
  // 中键拖动仅滚动工作区，不截获左键元素选择、框选或拖拽。
  let pan: { x:number; y:number; left:number; top:number } | undefined;
  stage?.addEventListener("pointerdown", event => {
    if (event.button !== 1) return;
    event.preventDefault(); stage.setPointerCapture(event.pointerId);
    pan = { x:event.clientX, y:event.clientY, left:stage.scrollLeft, top:stage.scrollTop };
  });
  stage?.addEventListener("pointermove", event => {
    if (pan) { stage.scrollLeft = pan.left + pan.x - event.clientX; stage.scrollTop = pan.top + pan.y - event.clientY; }
  });
  stage?.addEventListener("pointerup", () => { pan = undefined; });
  stage?.addEventListener("pointercancel", () => { pan = undefined; });
  const workspaceRoot = document.querySelector<HTMLElement>(".workspace");
  // 窄视口初次进入时收起两侧；用户仍可独立展开，不改变模板或缩放比例。
  if (workspaceRoot && window.innerWidth < 1100) {
    workspaceRoot.classList.add("left-collapsed","right-collapsed");
    for (const side of ["left","right"]) document.getElementById(`toggle-${side}`)?.setAttribute("aria-expanded","false");
  }
  /** 拖动仅设置外壳列宽；资源栏 180—300px、属性栏 320—440px，并为画布保留至少 400px。 */
  for (const side of ["left","right"]) {
    if (!workspaceRoot) break;
    const handle = document.createElement("div"); handle.className = `panel-resize resize-${side}`;
    handle.setAttribute("role","separator"); handle.setAttribute("aria-orientation","vertical");handle.tabIndex=0;
    handle.setAttribute("aria-label",side==='left'?"Resize resources / 调整资源栏宽度":"Resize properties / 调整属性栏宽度");
    handle.setAttribute("aria-valuemin",side==='left'?"180":"320");handle.setAttribute("aria-valuemax",side==='left'?"300":"440");
    const resize = (delta:number) => {
      const panel = document.querySelector<HTMLElement>(`.side.${side}`)!;
      const other = document.querySelector<HTMLElement>(`.side.${side==='left'?'right':'left'}`)!;
      const max = Math.max(side==='left'?180:320, Math.min(side==='left'?300:440, workspaceRoot.clientWidth-other.getBoundingClientRect().width-400));
      const width=Math.max(side==='left'?180:320,Math.min(max,panel.getBoundingClientRect().width+delta));
      workspaceRoot.style.setProperty(side==='left'?"--resources-width":"--properties-width",`${width}px`);
      handle.setAttribute("aria-valuenow",String(Math.round(width)));
    };
    handle.addEventListener("keydown", event => {
      if(event.key!=="ArrowLeft"&&event.key!=="ArrowRight")return;
      event.preventDefault();resize((event.key==="ArrowRight"?16:-16)*(side==='left'?1:-1));
    });
    handle.addEventListener("pointerdown", event => {
      event.preventDefault();handle.setPointerCapture(event.pointerId);let x=event.clientX;
      const move=(current:PointerEvent)=>{resize((current.clientX-x)*(side==='left'?1:-1));x=current.clientX};
      const finish=()=>{handle.removeEventListener("pointermove",move);handle.removeEventListener("pointerup",finish);handle.removeEventListener("pointercancel",finish)};
      handle.addEventListener("pointermove",move);handle.addEventListener("pointerup",finish);handle.addEventListener("pointercancel",finish);
    });
    workspaceRoot.append(handle);
  }
  /** 原生属性行按语义分组；只移动同一父容器的原节点，保留上游事件与元素专属字段。 */
  const settingsRoot = document.getElementById("settings");
  if (settingsRoot) {
    const observer = new MutationObserver(() => {
      const rows = Array.from(settingsRoot.querySelectorAll<HTMLElement>(".hiprint-option-item-row:not([data-workbench-grouped])"));
      for (const row of rows) {
        const owner = row.parentElement!;
        if (owner.classList.contains("property-fields")) continue;
        row.dataset.workbenchGrouped = "true";
        const label = row.querySelector(".hiprint-option-item-label")?.textContent?.trim() ?? "";
        const english = document.documentElement.lang === "en";
        const index = /分页|页眉|页脚|打印|奇数|偶数|首页|尾页|page|print|footer|header/i.test(label)?4:/边框|背景|border|background/i.test(label)?3:/字体|字号|颜色|加粗|斜体|对齐|行高|间距|font|color|align|line height/i.test(label)?2:/宽度|高度|坐标|位置|width|height|left|top/i.test(label)?1:0;
        const names = english?["Basics","Position & size","Font & text","Border & background","Printing & pagination"]:["基础属性","位置与尺寸","字体与文本","边框与背景","打印与分页"];
        let section = Array.from(owner.children).find(child=>child instanceof HTMLElement&&child.dataset.propertySection===String(index)) as HTMLElement|undefined;
        if (!section) {
          const details = document.createElement("details"); details.open = index<2; details.className="property-section";details.dataset.propertySection=String(index);
          const title=document.createElement("summary");title.textContent=names[index];const fields=document.createElement("div");fields.className="property-fields";
          details.append(title,fields); owner.append(details); section=details;
        }
        if(index===1)row.classList.add("compact-property");
        section.querySelector(".property-fields")!.append(row);
      }
      for(const section of settingsRoot.querySelectorAll(".property-section"))if(!section.querySelector(".hiprint-option-item-row"))section.remove();
    });
    observer.observe(settingsRoot,{childList:true,subtree:true});
  }
  let template: any;
  let zoom = 1;
  let quickPanelOpen=false;
  /** 只记录真实元素点击以切换面板，含表格；不据此伪造引擎选中集合。 */
  let selectionRevision=0;
  document.getElementById("designer")?.addEventListener("click",event=>{
    if((event.target as HTMLElement).closest(".hiprint-printElement"))selectionRevision++;
  });
  // 剪贴板仅保存在本次隔离会话内，不读写系统剪贴板或浏览器存储。
  let clipboard:any[] = [];
  /** 仅调用上游 zoom，第二参数 false 防止视图缩放写入模板 JSON；所有面板保持一致。 */
  const applyZoom = (value:number) => {
    zoom = Math.max(.25, Math.min(2, value));
    for (const panel of template.printPanels) {
      const savedScale = panel.scale; panel.zoom(zoom, false); panel.scale = savedScale;
    }
  };
  /** 分组移动已有节点，tid 和拖拽事件保持原样；未知扩展收纳到基础组件。 */
  const organize = (english:boolean) => {
    const root = document.getElementById("elements");
    if (!root) return;
    const nodes = Array.from(root.querySelectorAll<HTMLElement>(".ep-draggable-item"));
    const groups = [english?"Basic":"基础组件", english?"Tables":"表格组件", english?"Helpers":"辅助组件", english?"Codes":"编码组件"];
    const containers = groups.map(title => {
      const section = document.createElement("section"), heading = document.createElement("h3"), list = document.createElement("ul");
      section.className = "hiprint-printElement-type"; heading.className = "title"; heading.textContent = title;
      section.append(heading,list); return { section,list };
    });
    // 本地 Remix Icon SVG 与平台保持同一来源，不请求外部图标服务。
    const icons:Record<string,string> = {"text": "<path fill=\"currentColor\" d=\"M13 6v15h-2V6H5V4h14v2z\"/>", "image-line": "<path fill=\"currentColor\" d=\"M2.992 21A.993.993 0 0 1 2 20.007V3.993A1 1 0 0 1 2.992 3h18.016c.548 0 .992.445.992.993v16.014a1 1 0 0 1-.992.993zM20 15V5H4v14L14 9zm0 2.828l-6-6L6.828 19H20zM8 11a2 2 0 1 1 0-4a2 2 0 0 1 0 4\"/>", "file-text-line": "<path fill=\"currentColor\" d=\"M21 8v12.993A1 1 0 0 1 20.007 22H3.993A.993.993 0 0 1 3 21.008V2.992C3 2.455 3.449 2 4.002 2h10.995zm-2 1h-5V4H5v16h14zM8 7h3v2H8zm0 4h8v2H8zm0 4h8v2H8z\"/>", "table-line": "<path fill=\"currentColor\" d=\"M4 8h16V5H4zm10 11v-9h-4v9zm2 0h4v-9h-4zm-8 0v-9H4v9zM3 3h18a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1\"/>", "code-box-line": "<path fill=\"currentColor\" d=\"M3 3h18a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1m1 2v14h16V5zm16 7l-3.535 3.536l-1.415-1.415L17.172 12L15.05 9.879l1.415-1.415zM6.828 12l2.122 2.121l-1.414 1.415L4 12l3.536-3.536L8.95 9.88zm4.416 5H9.116l3.64-10h2.128z\"/>", "separator": "<path fill=\"currentColor\" d=\"M2 11h2v2H2zm4 0h12v2H6zm14 0h2v2h-2z\"/>", "layout-column-line": "<path fill=\"currentColor\" d=\"M11 5H5v14h6zm2 0v14h6V5zM4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1\"/>", "rectangle-line": "<path fill=\"currentColor\" d=\"M3 4h18a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1m1 2v12h16V6z\"/>", "checkbox-blank-circle-line": "<path fill=\"currentColor\" d=\"M12 22C6.477 22 2 17.523 2 12S6.477 2 12 2s10 4.477 10 10s-4.477 10-10 10m0-2a8 8 0 1 0 0-16a8 8 0 0 0 0 16\"/>", "barcode-line": "<path fill=\"currentColor\" d=\"M2 4h2v16H2zm4 0h1v16H6zm2 0h2v16H8zm3 0h2v16h-2zm3 0h2v16h-2zm3 0h1v16h-1zm2 0h3v16h-3z\"/>", "qr-code-line": "<path fill=\"currentColor\" d=\"M16 17v-1h-3v-3h3v2h2v2h-1v2h-2v2h-2v-3h2v-1zm5 4h-4v-2h2v-2h2zM3 3h8v8H3zm2 2v4h4V5zm8-2h8v8h-8zm2 2v4h4V5zM3 13h8v8H3zm2 2v4h4v-4zm13-2h3v2h-3zM6 6h2v2H6zm0 10h2v2H6zM16 6h2v2h-2z\"/>"};
    for (const node of nodes) {
      const tid = node.getAttribute("tid") ?? "";
      const group = /table/i.test(tid)?1:/hline|vline|rect|oval/i.test(tid)?2:/barcode|qrcode/i.test(tid)?3:0;
      const name = /qrcode/i.test(tid)?"qr-code-line":/barcode/i.test(tid)?"barcode-line":/image/i.test(tid)?"image-line":/table/i.test(tid)?"table-line":/html/i.test(tid)?"code-box-line":/longText/i.test(tid)?"file-text-line":/hline/i.test(tid)?"separator":/vline/i.test(tid)?"layout-column-line":/rect/i.test(tid)?"rectangle-line":/oval/i.test(tid)?"checkbox-blank-circle-line":"text";
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg"); svg.setAttribute("viewBox","0 0 24 24"); svg.setAttribute("fill","currentColor"); svg.setAttribute("aria-hidden","true"); svg.innerHTML = icons[name] ?? icons.text;
      node.title = node.textContent?.trim() ?? "";
      node.prepend(svg);
      const item = document.createElement("li"); item.append(node); containers[group].list.append(item);
    }
    root.replaceChildren(...containers.filter(group=>group.list.childElementCount).map(group=>group.section));
  };
  /** 契约文案一律通过 textContent 写入，服务端字段不能注入隔离文档 HTML。 */
  const sources = (contract:any, english:boolean) => {
    const root = document.getElementById("resource-sources"); if (!root) return;
    root.replaceChildren();
    const hint = document.createElement("p"); hint.className = "capability-note";
    hint.textContent = contract ? (english?"Registered design fields · preview values are examples":"已注册设计字段 · 预览值为示例数据") : (english?"No data contract selected. Select one in template settings.":"尚未选择数据契约，请在模板设置中选择。") ;
    root.append(hint);
    for (const field of contract?.fields ?? []) {
      const group = document.createElement("details"), title = document.createElement("summary");
      group.className = "source-group"; group.open = true; title.textContent = english?field.labelEn:field.labelZh; group.append(title);
      for (const column of field.columns ?? [field]) {
        const row = document.createElement("div"); row.className = "source-field";
        row.textContent = `${english?column.labelEn:column.labelZh} · ${column.key}`; group.append(row);
      }
      root.append(group);
    }
  };
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
    template.design("#designer");applyZoom(zoom);
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
        document.documentElement.lang = payload.language === "en" ? "en" : "zh-CN";
        library.disAutoConnect();
        library.hiprint.init({ providers: [new library.defaultElementTypeProvider()], lang: payload.language });
        // 默认提供器注册在 defaultModule；省略模块名会读取空的 _default，导致元素栏为空。
        library.hiprint.PrintElementTypeManager.build("#elements", "defaultModule");
        organize(payload.language === "en"); sources(payload.contract, payload.language === "en");
        template = new library.hiprint.PrintTemplate({ template: payload.template, history: true,
          settingContainer: "#settings", paginationContainer: "#pagination" });
        template.design("#designer");
        remember();
      } else if (action === "state") {
        remember();
        const panel = template.editingPanel;
        send({ id, value: { serialized:JSON.stringify(template.getJson()), undo:history.length>1, redo:future.length>0,
          rightWidth:document.querySelector(".side.right")?.getBoundingClientRect().width||360, selectionRevision, selected:template.getSelectEls().length, clipboard:clipboard.length>0, width:panel.width, height:panel.height, pages:template.printPanels.length, zoom } });
        return;
      } else if (action === "workspace") {
        if(payload.action === "quickPanel") {
          const root=document.querySelector(".workspace")!;
          quickPanelOpen=!!payload.value;root.classList.toggle("quick-panel-active",quickPanelOpen);if(quickPanelOpen)root.classList.remove("right-collapsed");
        } else if (["copy","paste","delete"].includes(payload.action)) {
          const panel = template.editingPanel, selected = template.getSelectEls();
          const indices = selected.map((element:any)=>panel.printElements.indexOf(element));
          const current = JSON.parse(JSON.stringify(template.getJson()));
          const target = current.panels[template.printPanels.indexOf(panel)];
          if(payload.action === "copy") clipboard = indices.map((index:number)=>target.printElements[index]);
          else if(payload.action === "delete" && indices.length) {
            remember(); target.printElements = target.printElements.filter((_element:any,index:number)=>!indices.includes(index)); rebuild(current); remember();
          } else if(payload.action === "paste" && clipboard.length) {
            const copies = JSON.parse(JSON.stringify(clipboard));
            copies.forEach((element:any)=>{element.options.left=Number(element.options.left??0)+10;element.options.top=Number(element.options.top??0)+10});
            remember(); target.printElements.push(...copies); rebuild(current); remember();
          }
        } else if(payload.action === "zoom" && Number.isFinite(payload.value)) applyZoom(payload.value);
        else if(payload.action === "fit") applyZoom((stage!.clientWidth-80)/(Number(template.editingPanel.width)*96/25.4));
        else if(payload.action === "align" && ["left","right","top","bottom","vertical","horizontal"].includes(payload.value)) {
          remember(); template.setElsAlign(payload.value); remember();
        } else if(payload.action === "gridSpacing" && Number.isFinite(payload.value) && payload.value>=2 && payload.value<=40) stage!.style.setProperty("--grid-spacing",`${payload.value}pt`);
        else if(payload.action === "grid") stage!.classList.toggle("no-grid", !payload.value);
        else if(payload.action === "ruler") stage!.classList.toggle("no-ruler", !payload.value);
        else throw new Error("PRINT_OPERATION_UNSUPPORTED");
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
      } else throw new Error("PRINT_OPERATION_UNSUPPORTED");
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
