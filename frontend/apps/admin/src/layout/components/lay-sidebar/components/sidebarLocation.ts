/**
 * 动态菜单按路径生成 RouterLink 目标，避免租户切换时旧菜单 name 已被移除而中断渲染。
 * @param item 已经由导航目录校验的站内菜单，路径不是任意外链或身份来源
 */
export function sidebarLocation(item:{path?:string}) {return {path:item.path||'/'};}
