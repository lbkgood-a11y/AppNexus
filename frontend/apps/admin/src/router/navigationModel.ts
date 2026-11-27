import type { MenuNode } from "@appnexus/configuration";

/** 本人导航响应；租户来自服务端，客户端仅用版本隔离过期响应。 */
export interface NavigationSnapshot {
  tenantId: string | null;
  contextVersion: number;
  items: MenuNode[];
}

/** 模板菜单结构；目录路径为内部稳定标识，外链不作为本地页面注册。 */
export interface NavigationMenu {
  path: string;
  name: string;
  meta: { title: Record<string,string>; icon?: string; rank: number; showLink: boolean; showParent: boolean; navigation: boolean };
  children?: NavigationMenu[];
}

/**
 * 根据后端有序清单构树；过滤隐藏项后保持相对顺序，空目录不展示。
 * @param items 本人已授权入口，隐藏本地页面仍须由路由注册器单独处理
 * @returns 不包含按钮、隐藏祖先下入口或空目录的模板导航
 */
export function buildNavigationMenus(items: MenuNode[]): NavigationMenu[] {
  const ordered=[...items].sort((a,b)=>a.sortOrder-b.sortOrder||compare(a.code,b.code)||compare(a.id,b.id));
  const visit=(parentId:string|null,ancestors:Set<string>):NavigationMenu[]=>ordered
    .filter(item=>(item.parentId??null)===parentId&&item.visible&&item.enabled&&item.type!=="BUTTON")
    .flatMap(item=>{
      if(ancestors.has(item.id))throw new Error("Invalid navigation hierarchy");
      const children=visit(item.id,new Set([...ancestors,item.id]));
      if(item.type==="DIRECTORY"&&!children.length)return [];
      return [{path:item.type==="EXTERNAL"?item.externalUrl!:item.type==="DIRECTORY"?`/navigation/${item.id}`:item.path!,
        name:item.type==="EXTERNAL"?item.externalUrl!:`Navigation_${item.id}`,
        meta:{title:{"zh-CN":item.titleZh,zh:item.titleZh,en:item.titleEn},icon:navigationIcon(item),rank:item.sortOrder,
          showLink:true,showParent:true,navigation:true},
        ...(item.type==="DIRECTORY"?{children}:{})}];
    });
  return visit(null,new Set());
}

// 具体职责排在领域前面，避免同一领域的库存、权限、配置统一显示成目录图标。
const navigationIconThemes = [
  [/attachment|附件/i, "ri/attachment-line"],
  [/inventory|库存/i, "ri/archive-line"],
  [/event|事件/i, "ri/history-line"],
  [/authorization|permission|授权|权限/i, "ri/shield-keyhole-line"],
  [/roles?|角色/i, "ri/group-line"],
  [/user|personnel|account|人员|账号/i, "ri/user-settings-line"],
  [/organization|组织/i, "ri/organization-chart"],
  [/print|打印/i, "ri/printer-line"],
  [/workflow|工作流|审批/i, "ri/flow-chart"],
  [/dictionar|字典/i, "ri/book-2-line"],
  [/inbound|入仓/i, "ri/folder-line"],
  [/warehouse|warehousing|仓储|仓库/i, "ri/store-2-line"],
  [/product|商品/i, "ri/shopping-bag-3-line"],
  [/master.?data|主数据/i, "ri/database-2-line"],
  [/configuration|settings|配置|设置/i, "ri/settings-3-line"],
  [/document|单据/i, "ri/file-list-line"]
] as const;

/** 解析菜单和路由共同使用的图标。显式配置优先，空值时按职责或入口类型回退到本地图标。 */
export function navigationIcon(item: MenuNode): string {
  const configured = item.icon?.trim();
  if (configured) return configured;
  if (item.type === "EXTERNAL") return "ri/links-fill";
  const topic = [item.component, item.code, item.titleZh, item.titleEn].join(" ");
  const match = navigationIconThemes.find(([pattern]) => pattern.test(topic));
  return match?.[1] ?? (item.type === "DIRECTORY" ? "ri/menu-line" : "ri/file-info-line");
}

/** 使用与服务端 code 排序相同的字符序，避免浏览器语言改变同值排序。 */
function compare(a:string,b:string){return a<b?-1:a>b?1:0;}

/** 外链仅允许 HTTP/HTTPS；不允许脚本、用户凭据、反斜杠或控制字符。 */
export function safeNavigationUrl(value:string|undefined|null):boolean {
  if(!value||value.length>2000||/[\s\\\u0000-\u001f\u007f]/.test(value))return false;
  try{const url=new URL(value);return /^https?:\/\//.test(value)&&["http:","https:"].includes(url.protocol)&&!url.username&&!url.password&&Boolean(url.hostname);}
  catch{return false;}
}
