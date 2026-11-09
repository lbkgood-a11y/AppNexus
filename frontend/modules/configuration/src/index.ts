export const CONFIGURATION_MODULE = "configuration" as const;

/** 菜单资源类型；BUTTON 只表达操作权限，不产生页面路由。 */
export type MenuType = "DIRECTORY" | "MENU" | "BUTTON" | "EXTERNAL";
/** 入口适用域；登录公共入口不赋予外部应用的业务权限。 */
export type MenuScope = "PLATFORM" | "TENANT" | "AUTHENTICATED";
/** 已注册构建内页面；path 和基础权限由页面契约确定。 */
export interface NavigationPage {key:string;path:string;titleZh:string;titleEn:string;scope:MenuScope;permission:string|null;gate:string}
/** 平台菜单节点，version 用于阻止管理员之间的静默覆盖。 */
export interface MenuNode { id:string;parentId?:string|null;type:MenuType;code:string;titleZh:string;titleEn:string;path?:string|null;component?:string|null;icon?:string|null;permission?:string|null;sortOrder:number;visible:boolean;enabled:boolean;version:number;scope:MenuScope;externalUrl?:string|null }
/** 创建和更新请求字段；更新版本由 API 适配器从 MenuNode 单独提交。 */
export type MenuInput=Omit<MenuNode,"id"|"version">;
/** 菜单管理请求边界；领域组件不依赖应用内 HTTP 或 CSRF 实现。 */
export interface MenuManagementApi { pages:()=>Promise<NavigationPage[]>;list:()=>Promise<MenuNode[]>;create:(input:MenuInput)=>Promise<MenuNode>;update:(id:string,version:number,input:MenuInput)=>Promise<MenuNode>;remove:(id:string,version:number)=>Promise<void> }

/** 租户业务字典状态；停用保留历史含义，但不应进入新的业务选择。 */
export type DictionaryStatus="ENABLED"|"DISABLED";
/** 字典类型，code 创建后不可修改，version 用于乐观并发控制。 */
export interface BusinessDictionary {id:string;code:string;name:string;description?:string|null;status:DictionaryStatus;sortOrder:number;version:number;updatedAt:string}
/** 层级字典项；value 是业务持久化稳定值，referenced 表示禁止直接删除。 */
export interface DictionaryItem {id:string;dictionaryId:string;parentId?:string|null;code:string;value:string;name:string;description?:string|null;status:DictionaryStatus;sortOrder:number;referenced:boolean;version:number;updatedAt:string}
/** 字典管理请求边界；组件不依赖应用 HTTP、CSRF 或租户上下文实现。 */
export interface DictionaryManagementApi {
 /** 当前成员操作能力；只控制展示，后端仍逐请求检查。 */
 capabilities:()=>Promise<string[]>;
 list:()=>Promise<BusinessDictionary[]>;create:(input:Omit<BusinessDictionary,"id"|"status"|"version"|"updatedAt">)=>Promise<BusinessDictionary>;
 update:(id:string,input:Pick<BusinessDictionary,"name"|"description"|"status"|"sortOrder"|"version">)=>Promise<BusinessDictionary>;remove:(id:string,version:number)=>Promise<void>;
 items:(dictionaryId:string)=>Promise<DictionaryItem[]>;createItem:(dictionaryId:string,input:Omit<DictionaryItem,"id"|"dictionaryId"|"status"|"referenced"|"version"|"updatedAt">)=>Promise<DictionaryItem>;
 updateItem:(dictionaryId:string,itemId:string,input:Pick<DictionaryItem,"parentId"|"name"|"description"|"status"|"sortOrder"|"version">)=>Promise<DictionaryItem>;removeItem:(dictionaryId:string,itemId:string,version:number)=>Promise<void>;
}
