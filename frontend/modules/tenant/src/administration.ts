/** 平台目录摘要，不含密码或认证哈希；平台身份不代表租户成员身份。 */
/** 全局账号资料；联系方式只有在服务端验证后才可用于登录或找回。 */
export interface DirectoryUser {
 id: string;
 username: string;
 displayName: string;
 email: string|null;
 emailVerified: boolean;
 phoneNumber: string|null;
 phoneVerified: boolean;
 enabled: boolean;
}
/** 租户开通状态独立于成员及全局账号状态。 */
export interface DirectoryTenant { id: string; name: string; enabled: boolean }
/** 成员是一个账号在指定租户内的身份，停用后恢复不自动恢复授权。 */
export interface DirectoryMember { memberId: string; accountId: string; username: string; displayName: string; enabled: boolean }
/** 服务端分页，不能用当前页条数伪造总数。 */
export interface DirectoryPage<T> { items: T[]; total: number }
/** 应用壳注入真实 API，领域页面不依赖模板内部 HTTP 或会话实现。 */
export interface AdministrationApi {
  access(): Promise<{ administrator: boolean }>;
  users(page: number, query: string): Promise<DirectoryPage<DirectoryUser>>;
  tenants(page: number, query: string): Promise<DirectoryPage<DirectoryTenant>>;
  members(tenant: string, page: number, query: string): Promise<DirectoryPage<DirectoryMember>>;
  createUser(username: string, displayName: string, email: string, phoneNumber: string, password: string): Promise<DirectoryUser>;
  updateUser(id: string, displayName: string, email: string, phoneNumber: string): Promise<DirectoryUser>;
  createTenant(requestId: string, name: string, administratorAccountId: string): Promise<DirectoryTenant>;
  addMember(tenant: string, account: string): Promise<unknown>;
  userState(id: string, enabled: boolean): Promise<unknown>;
  tenantState(id: string, enabled: boolean): Promise<unknown>;
  memberState(tenant: string, member: string, enabled: boolean): Promise<unknown>;
}
