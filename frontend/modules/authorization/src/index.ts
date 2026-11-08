export const AUTHORIZATION_MODULE = "authorization" as const;
export type RoleStatus="ENABLED"|"DISABLED";
export type RoleType="SYSTEM"|"CUSTOM";
/** 角色列表项；version 必须随写操作回传以阻止并发静默覆盖。 */
export interface RoleItem{id:string;scopeType:"PLATFORM"|"TENANT";tenantId?:string|null;roleType:RoleType;systemKey?:string|null;code:string;name:string;description?:string|null;status:RoleStatus;sortOrder:number;version:number;updatedAt:string}
export interface RolePage{items:RoleItem[];total:number;page:number;pageSize:number}
export interface RoleQuery{query:string;status?:RoleStatus;roleType?:RoleType;page:number;pageSize:number}
export interface RoleCreate{code:string;name:string;description:string;sortOrder:number}
export interface RoleUpdate{name:string;description:string;sortOrder:number;version:number}
/** 角色领域组件只依赖此端口，不读取应用会话、租户标识或 CSRF 实现。 */
export interface RoleManagementApi{list:(query:RoleQuery)=>Promise<RolePage>;create:(input:RoleCreate)=>Promise<RoleItem>;update:(id:string,input:RoleUpdate)=>Promise<RoleItem>;changeStatus:(id:string,status:RoleStatus,reason:string,version:number)=>Promise<RoleItem>;remove:(id:string,confirmationCode:string,reason:string,version:number)=>Promise<void>}
