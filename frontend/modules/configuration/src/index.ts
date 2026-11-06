export const CONFIGURATION_MODULE = "configuration" as const;

/** 菜单资源类型；BUTTON 只表达操作权限，不产生页面路由。 */
export type MenuType = "DIRECTORY" | "MENU" | "BUTTON";
/** 平台菜单节点，version 用于阻止管理员之间的静默覆盖。 */
export interface MenuNode { id:string;parentId?:string|null;type:MenuType;code:string;titleZh:string;titleEn:string;path?:string|null;component?:string|null;icon?:string|null;permission?:string|null;sortOrder:number;visible:boolean;enabled:boolean;version:number }
/** 创建和更新请求字段；更新版本由 API 适配器从 MenuNode 单独提交。 */
export type MenuInput=Omit<MenuNode,"id"|"version">;
/** 菜单管理请求边界；领域组件不依赖应用内 HTTP 或 CSRF 实现。 */
export interface MenuManagementApi { list:()=>Promise<MenuNode[]>;create:(input:MenuInput)=>Promise<MenuNode>;update:(id:string,version:number,input:MenuInput)=>Promise<MenuNode>;remove:(id:string,version:number)=>Promise<void> }
