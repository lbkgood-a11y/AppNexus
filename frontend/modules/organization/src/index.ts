export const ORGANIZATION_RESOURCE = "org.unit" as const;

/** 当前租户的组织操作；GRANT 只能经运维初始化，不能在普通授权界面委派。 */
export type OrganizationCapability = "READ" | "CREATE" | "UPDATE" | "DELETE" | "GRANT";
/** 组织摘要，租户归属仅由服务端上下文决定。 */
export interface OrganizationUnit { id: string; name: string }
/** 组织类型树节点；parentId 为空表示挂载租户根节点。 */
export interface OrganizationType { id: string; code: string; name: string; enabled: boolean; version: number }
export interface OrganizationTreeUnit extends OrganizationUnit { code: string; typeId: string; parentId: string | null; enabled: boolean; version: number }
export interface OrganizationRelation { id: string; relationType: string; sourceId: string; targetId: string; version: number }
export interface OrganizationPosition { id: string; organizationId: string; code: string; name: string; enabled: boolean; version: number }
export interface OrganizationAssignment { id: string; memberId: string; positionId: string; startsAt: string; endsAt: string | null; enabled: boolean; version: number }
export interface OrganizationLeader { organizationId: string; assignmentId: string }
export interface OrganizationDirectoryMember { id: string; displayName: string }
export interface OrganizationImpact { children: number; relations: number; positions: number; activeAssignments: number; leaders: number }
export interface BusinessOption { value: string; name: string }
/** 授权选择器成员摘要；仅 GRANT 持有者可读取。 */
export interface OrganizationMember { memberId: string; displayName: string; organizationId: string|null; organizationName: string|null }
/** 有效组织授权，到期时间为空表示永久有效，撤销后立即影响后续请求。 */
export interface OrganizationGrant { memberId: string; capability: OrganizationCapability; expiresAt: string | null }
/** 领域页面依赖的请求端口，由应用壳注入 HTTP、CSRF 和会话处理。 */
export interface OrganizationApi {
  capabilities(): Promise<OrganizationCapability[]>;
  types(): Promise<OrganizationType[]>;
  treeUnits(): Promise<OrganizationTreeUnit[]>;
  createType(code: string, name: string): Promise<OrganizationType>;
  updateType(id: string, input: { name: string; enabled: boolean; version: number }): Promise<OrganizationType>;
  createTreeUnit(input: { code: string; typeId: string; parentId: string | null; name: string }): Promise<OrganizationTreeUnit>;
  updateTreeUnit(id: string, input: { name: string; enabled: boolean; version: number }): Promise<OrganizationTreeUnit>;
  moveTreeUnit(id: string, parentId: string | null, version: number): Promise<OrganizationTreeUnit>;
  impact(id: string): Promise<OrganizationImpact>;
  relations(): Promise<OrganizationRelation[]>;
  createRelation(input: { relationType: string; sourceId: string; targetId: string }): Promise<OrganizationRelation>;
  removeRelation(id: string, version: number): Promise<void>;
  positions(): Promise<OrganizationPosition[]>;
  createPosition(input: { organizationId: string; code: string; name: string }): Promise<OrganizationPosition>;
  updatePosition(id: string, input: { name: string; enabled: boolean; version: number }): Promise<OrganizationPosition>;
  assignments(): Promise<OrganizationAssignment[]>;
  createAssignment(input: { memberId: string; positionId: string; startsAt: string; endsAt: string | null }): Promise<OrganizationAssignment>;
  updateAssignment(id: string, input: { endsAt: string | null; enabled: boolean; version: number }): Promise<OrganizationAssignment>;
  leaders(): Promise<OrganizationLeader[]>;
  setLeader(input: { organizationId: string; assignmentId: string; selected: boolean }): Promise<void>;
  directoryMembers(): Promise<OrganizationDirectoryMember[]>;
  dictionary(code:string):Promise<BusinessOption[]>;
}
