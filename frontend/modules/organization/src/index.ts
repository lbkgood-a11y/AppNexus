export const ORGANIZATION_RESOURCE = "org.unit" as const;

/** 当前租户的组织操作；GRANT 只能经运维初始化，不能在普通授权界面委派。 */
export type OrganizationCapability = "READ" | "CREATE" | "UPDATE" | "DELETE" | "GRANT";
/** 组织摘要，租户归属仅由服务端上下文决定。 */
export interface OrganizationUnit { id: string; name: string }
/** 授权选择器成员摘要；仅 GRANT 持有者可读取。 */
export interface OrganizationMember { memberId: string; displayName: string; organizationId: string|null; organizationName: string|null }
/** 有效组织授权，到期时间为空表示永久有效，撤销后立即影响后续请求。 */
export interface OrganizationGrant { memberId: string; capability: OrganizationCapability; expiresAt: string | null }
/** 领域页面依赖的请求端口，由应用壳注入 HTTP、CSRF 和会话处理。 */
export interface OrganizationApi {
  capabilities(): Promise<OrganizationCapability[]>;
  units(): Promise<OrganizationUnit[]>;
  create(name: string): Promise<unknown>;
  rename(id: string, name: string): Promise<unknown>;
  remove(id: string): Promise<unknown>;
  members(): Promise<OrganizationMember[]>;
  /** null 清除主要组织，组织归属不产生业务操作权限。 */
  assignOrganization(memberId:string,organizationId:string|null):Promise<unknown>;
  grants(memberId: string): Promise<OrganizationGrant[]>;
  /** @param expiresAt ISO 8601 UTC 时间点；null 表示永久，非空必须晚于当前时间 */
  grant(memberId: string, capability: OrganizationCapability, expiresAt: string | null): Promise<unknown>;
  revoke(memberId: string, capability: OrganizationCapability): Promise<unknown>;
}
