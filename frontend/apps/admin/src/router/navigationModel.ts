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
        meta:{title:{"zh-CN":item.titleZh,zh:item.titleZh,en:item.titleEn},icon:menuIcon(item),rank:item.sortOrder,
          showLink:true,showParent:true,navigation:true},
        ...(item.type==="DIRECTORY"?{children}:{})}];
    });
  return visit(null,new Set());
}

/** 未配置图标时按菜单职责提供本地默认图标，保证新旧目录、页面及外链都有可识别标识。 */
function menuIcon(item: MenuNode): string {
  return item.icon?.trim() || (item.type === "DIRECTORY" ? "ri/menu-line" : item.type === "EXTERNAL" ? "ri/links-fill" : "ri/file-info-line");
}

/** 使用与服务端 code 排序相同的字符序，避免浏览器语言改变同值排序。 */
function compare(a:string,b:string){return a<b?-1:a>b?1:0;}

/** 外链仅允许 HTTP/HTTPS；不允许脚本、用户凭据、反斜杠或控制字符。 */
export function safeNavigationUrl(value:string|undefined|null):boolean {
  if(!value||value.length>2000||/[\s\\\u0000-\u001f\u007f]/.test(value))return false;
  try{const url=new URL(value);return /^https?:\/\//.test(value)&&["http:","https:"].includes(url.protocol)&&!url.username&&!url.password&&Boolean(url.hostname);}
  catch{return false;}
}
