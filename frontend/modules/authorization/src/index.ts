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
/** 强制字段展示限制；不提供修改或查询权，多个角色命中时取更严格结果。 */
export interface FieldPolicy {resource:string;field:string;mode:"HIDDEN"|"MASKED"}
/** 仅列出后端已接通执行的字段，缺少目录的能力不得由前端编造。 */
export interface FieldDescriptor {resource:string;field:string;nameZh:string;nameEn:string;kind:"EMAIL"|"PHONE"|"PERSON_NAME"|"IDENTIFIER"|"BANK_ACCOUNT"|"TEXT";modes:FieldPolicy["mode"][]}
export interface AuthorizationAssignment{permissionIds:string[];dataPolicies:DataPolicy[];roleVersion:number;fieldPolicies?:FieldPolicy[]}
export interface AuthorizationOption{id:string;name:string}
/** 当前管理域的角色策略模板摘要，不携带业务原值。 */
export interface PolicyTemplate { id: string; name: string }
/** 授权工作台请求端口；完整快照保存由应用适配器附加 CSRF，不在领域组件读取会话。 */
/** 授权菜单元数据仅用于定位功能，resources 来自服务端受控页面映射。 */
export interface AuthorizationMenu{id:string;parentId:string|null;titleZh:string;titleEn:string;sortOrder:number;resources:string[];permission:string|null}
/** 模板能力仅在真实后端已接入的管理域提供；应用使用当前角色版本与原因。 */
export interface AuthorizationCenterApi{templates?:{list:(role:string)=>Promise<PolicyTemplate[]>;save:(role:string,name:string)=>Promise<PolicyTemplate>;apply:(role:string,template:string,version:number,reason:string)=>Promise<AuthorizationAssignment>};menus:()=>Promise<AuthorizationMenu[]>;permissions:()=>Promise<PermissionItem[]>;metadata:()=>Promise<ScopeSupport[]>;fields:()=>Promise<FieldDescriptor[]>;assignment:(roleId:string)=>Promise<AuthorizationAssignment>;replace:(roleId:string,input:{ids:string[];dataPolicies:DataPolicy[];fieldPolicies:FieldPolicy[];version:number;reason:string})=>Promise<AuthorizationAssignment>;organizationOptions?:()=>Promise<AuthorizationOption[]>}

/** 成员独立授权分支；expiresAt 为 UTC ISO 时间或 null（长期），角色增权不自动改变此快照。 */
export interface MemberGrant {roleId:string;permissionIds:string[];dataPolicies:DataPolicy[];expiresAt:string|null;roleVersion:number}
export interface MemberGrantAssignment {grants:MemberGrant[];authorityVersion:number}
/** 成员范围配置端口；版本属于成员全部授权，冲突后必须重新读取。 */
export interface MemberGrantApi {get:(member:string)=>Promise<MemberGrantAssignment>;replace:(member:string,input:{grant:MemberGrant;version:number;reason:string})=>Promise<MemberGrantAssignment>}

/** 授权主体展示项；ID 只用于请求定位，不代表操作者身份或租户凭据。 */
export interface AuthorizationSubject { id: string; name: string; detail?: string; enabled?: boolean }
/** 主体完整角色快照；authorityVersion 必须原样回传，冲突后须重新读取。 */
export interface SubjectAssignment { roleIds: string[]; authorityVersion: number }
/** 单个角色的权限贡献；解释仅供排错，不能作为业务放行凭据。 */
export interface PermissionSource { roleId: string; roleName: string; effective: boolean; inactiveReason: string | null; scopeType: DataScopeType | "DENY" | null; organizationIds: string[] }
/** 同一权限的全部角色来源，effective 表示至少一个完整分支生效。 */
export interface EffectivePermission { permissionCode: string; effective: boolean; sources: PermissionSource[] }
/** 主体页签端口；分页单位为条，应用适配器负责真实目录、CSRF 和可信管理域。 */
export interface SubjectAuthorizationApi {
  grants?:MemberGrantApi;
  subjects(page: number, query: string): Promise<{ items: AuthorizationSubject[]; total: number }>;
  assignment(subjectId: string): Promise<SubjectAssignment>;
  replace(subjectId: string, input: { ids: string[]; version: number; reason: string }): Promise<SubjectAssignment>;
  effective(subjectId: string): Promise<EffectivePermission[]>;
}
