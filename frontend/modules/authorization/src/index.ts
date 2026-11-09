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
export type DataScopeType="SELF"|"ORGANIZATION"|"ORGANIZATION_SUBTREE"|"ALL";
export interface PermissionItem{id:string;scopeType:"PLATFORM"|"TENANT";code:string;resource:string;action:string;nameZh:string;nameEn:string;description:string;enabled:boolean;systemManaged:boolean;version:number}
export interface ScopeSupport{permissionId:string;permissionCode:string;scopeType:DataScopeType;resourceType:string}
export interface DataPolicy{permissionId:string;scopeType:DataScopeType;resourceType:string;organizationIds:string[]}
export interface AuthorizationAssignment{permissionIds:string[];dataPolicies:DataPolicy[];roleVersion:number}
export interface AuthorizationOption{id:string;name:string}
/** 授权工作台请求端口；完整快照保存由应用适配器附加 CSRF，不在领域组件读取会话。 */
export interface AuthorizationCenterApi{permissions:()=>Promise<PermissionItem[]>;metadata:()=>Promise<ScopeSupport[]>;assignment:(roleId:string)=>Promise<AuthorizationAssignment>;replace:(roleId:string,input:{ids:string[];dataPolicies:DataPolicy[];version:number;reason:string})=>Promise<AuthorizationAssignment>;organizationOptions?:()=>Promise<AuthorizationOption[]>}
