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
export type DataScopeType="SELF"|"ORGANIZATION"|"ORGANIZATION_SUBTREE"|"ALL"|"RULES";
export interface PermissionItem{id:string;scopeType:"PLATFORM"|"TENANT";code:string;resource:string;action:string;nameZh:string;nameEn:string;description:string;enabled:boolean;systemManaged:boolean;version:number}
export interface ScopeSupport{permissionId:string;permissionCode:string;scopeType:DataScopeType;resourceType:string}
export interface DataPolicy{permissionId:string;scopeType:DataScopeType;resourceType:string;organizationIds:string[];rules?:DataGrantRule[]}
/** 强制字段展示限制；不提供修改或查询权，多个角色命中时取更严格结果。 */
export interface FieldPolicy {resource:string;field:string;mode:"HIDDEN"|"MASKED"|"READ_ONLY"}
/** 仅列出后端已接通执行的字段，缺少目录的能力不得由前端编造。 */
export interface FieldDescriptor {resource:string;field:string;nameZh:string;nameEn:string;kind:"EMAIL"|"PHONE"|"PERSON_NAME"|"IDENTIFIER"|"BANK_ACCOUNT"|"TEXT";modes:FieldPolicy["mode"][]}
/** 角色完整配置；inheritingMemberCount 为当前有效继承成员数（人），缺省0，仅作影响提示。 */
export interface AuthorizationAssignment{permissionIds:string[];dataPolicies:DataPolicy[];roleVersion:number;fieldPolicies?:FieldPolicy[];inheritingMemberCount?:number;dataAccessVersion?:string}
export interface AuthorizationOption{id:string;name:string}
/** 记录候选查询复用业务受控列表；不授予额外读取权，回调只返回配置所需标识与标签。 */
export interface DataRecordSearch {
 /** 支持的资源来自应用固定映射，未接通资源不能显示虚假的搜索入口。 */
 resources:readonly string[];
 /** 关键词为名称或编码，最长200字符；page从1开始，每页20条，total为同一受控查询总数。 */
 search:(resource:string,query:string,page:number)=>Promise<{items:AuthorizationOption[];total:number}>;
 /** 批量回显已保存记录名，单批最多 100 个 ID，仍受对应对象读取权限限制。 */
 resolve?:(resource:string,ids:string[])=>Promise<AuthorizationOption[]>;
}
/** 当前管理域的角色策略模板摘要，不携带业务原值。 */
export interface PolicyTemplate { id: string; name: string }
/** 授权工作台请求端口；完整快照保存由应用适配器附加 CSRF，不在领域组件读取会话。 */
/** 授权菜单仅定位功能；operations 精确登记真实操作，resources 为元数据，缺少 operations 时不展开整个资源。 */
export interface AuthorizationMenu{id:string;parentId:string|null;titleZh:string;titleEn:string;sortOrder:number;resources:string[];permission:string|null;operations?:string[]}
/** 模板能力仅在真实后端已接入的管理域提供；应用使用当前角色版本与原因。 */
/** 代码声明只读投影；资源能力版本不由管理员填写，操作引用已有功能目录。 */
/** 受控条件维度，来源于业务对象代码声明，不允许输入 SQL 字段。 */
export interface DataDimension{field:string;nameZh:string;nameEn:string;valueType:"UUID"|"TEXT";operators:("EQ"|"IN")[];source?:"TEXT"|"ORGANIZATION"|"DICTIONARY"|"ACCOUNT"|"PERSON";dictionary?:string;operationCodes?:string[];parameters?:DataConditionParameter[]}
/** 动态参数是已登记的上下文来源，保存时不携带身份 UUID。 */
export type DataConditionParameter="CURRENT_ACCOUNT"|"CURRENT_PERSON"|"CURRENT_ORGANIZATION"|"CURRENT_ORGANIZATION_SUBTREE";
/** 同条规则内记录与所有条件取 AND；多条规则取 OR，空范围必须显式 allRecords。 */
export interface DataGrantRule{subjectType:"ROLE_MEMBERS"|"MEMBER"|"ORGANIZATION";subjectIds:string[];recordIds:string[];conditions:{field:string;operator:"EQ"|"IN";values:string[];parameter?:DataConditionParameter|null}[];allRecords:boolean}
/** 已声明字典的候选；禁用项只用于回显原策略。 */
export type DataConditionOptions=Record<string,{value:string;name:string;enabled:boolean}[]>;
export interface DataAccessDeclaration{objectType:string;version:number;operations?:{operationId:string;permissionCode:string}[];dimensions?:DataDimension[];resources:{resource:string;parentResource:string;nameZh:string;nameEn:string;fields:FieldDescriptor[]}[]}
export interface AuthorizationCenterApi{recordSearch?:DataRecordSearch;conditionOptions?:()=>Promise<DataConditionOptions>;declarations?:()=>Promise<DataAccessDeclaration[]>;templates?:{list:(role:string)=>Promise<PolicyTemplate[]>;save:(role:string,name:string)=>Promise<PolicyTemplate>;apply:(role:string,template:string,version:number,reason:string)=>Promise<AuthorizationAssignment>};menus:()=>Promise<AuthorizationMenu[]>;permissions:()=>Promise<PermissionItem[]>;metadata:()=>Promise<ScopeSupport[]>;fields:()=>Promise<FieldDescriptor[]>;assignment:(roleId:string)=>Promise<AuthorizationAssignment>;replace:(roleId:string,input:{ids:string[];dataPolicies:DataPolicy[];fieldPolicies:FieldPolicy[];version:number;reason:string;dataAccessVersion?:string})=>Promise<AuthorizationAssignment>;organizationOptions?:()=>Promise<AuthorizationOption[]>}

/** 成员角色分支；功能持续跟随角色，expiresAt 为 UTC ISO 时间或 null（长期）。 */
/** 成员范围模式：true 持续继承角色；false 使用独立配置；修改须显式保存。 */
export interface MemberGrant {inheritDataScope:boolean;roleId:string;permissionIds:string[];dataPolicies:DataPolicy[];expiresAt:string|null;roleVersion:number}
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
  /** 指定主体操作诊断；服务端复核管理域，不作为业务执行凭证。 */
  diagnostics(subjectId:string):Promise<OperationDiagnosis[]>;
  grants?:MemberGrantApi;
  subjects(page: number, query: string): Promise<{ items: AuthorizationSubject[]; total: number }>;
  assignment(subjectId: string): Promise<SubjectAssignment>;
  replace(subjectId: string, input: { ids: string[]; version: number; reason: string }): Promise<SubjectAssignment>;
  effective(subjectId: string): Promise<EffectivePermission[]>;
}

/** 操作权限诊断；明确保留未检查项，不把功能权限当作最终执行资格。 */
export interface OperationDiagnosis {
 operation:{id:string;nameZh:string;nameEn:string;pages:string[];permissions:string[];policy:string};
 status:"FUNCTION_GRANTED"|"NOT_GRANTED"|"OBJECT_CHECK_REQUIRED";
 permissions:EffectivePermission[];
 unchecked:string[];
 /** 来自执行端口的数据范围及字段限制，不代表具体对象可访问。 */
 data:{permission:string;resource:string;scope:string;organizations:string[];fields:Record<string,string>}[];
 /** 由真实导航准入规则检查的页面结果；可访问与菜单可见分别表达。 */
 pages:{page:string;path:string;accessible:boolean;visible:boolean;reasons:string[]}[];
}


/** 从授权目录选出本资源、本操作实际接通的条件字段。 */
export function conditionDimensions(declarations:DataAccessDeclaration[],resource:string|undefined,permissionCode:string|undefined):DataDimension[]{
 const declaration=declarations.find(item=>item.resources[0]?.resource===resource);
 return declaration?.dimensions?.filter(dimension=>!dimension.operationCodes?.length||
   declaration.operations?.some(operation=>operation.permissionCode===permissionCode&&
     dimension.operationCodes?.some(code=>operation.operationId===declaration.objectType+"."+code)))??[];
}
