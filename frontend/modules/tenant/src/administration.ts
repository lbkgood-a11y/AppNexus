/** 平台目录摘要，不含密码或认证哈希；平台身份不代表租户成员身份。 */
/** 全局账号资料；联系方式只有在服务端验证后才可用于登录或找回。 */
export interface DirectoryUser {
 id: string;
 username: string;
 displayName: string;
 /** 后端已隐藏或脱敏的字段名；编辑不得提交这些字段的展示占位值。 */
 restrictedFields?: string[];
 email: string|null;
 emailVerified: boolean;
 phoneNumber: string|null;
 phoneVerified: boolean;
 enabled: boolean;
}
/** 租户开通状态独立于成员及全局账号状态。 */
export interface DirectoryTenant { id: string; name: string; enabled: boolean }
/** 成员是一个账号在指定租户内的身份，停用后恢复不自动恢复授权。 */
export interface DirectoryMember { memberId: string; accountId: string; username: string; displayName: string; enabled: boolean; removed: boolean }
/** 服务端分页，不能用当前页条数伪造总数。 */
export interface DirectoryPage<T> { items: T[]; total: number }
/** 集成主体独立于账号成员，固定租户；version 用于并发更新保护。 */
export interface IntegrationClient { id: string; tenantId: string; name: string; enabled: boolean; version: number; scopes: string[] }
/** 密钥仅在创建或轮换响应出现一次，不得写入日志或持久存储。 */
export interface IntegrationSecret { client: IntegrationClient; secret: string }
/** 应用壳注入真实 API，领域页面不依赖模板内部 HTTP 或会话实现。 */
export interface AdministrationApi {
  access(): Promise<{ administrator: boolean }>;
  users(page: number, query: string): Promise<DirectoryPage<DirectoryUser>>;
  tenants(page: number, query: string): Promise<DirectoryPage<DirectoryTenant>>;
  members(tenant: string, page: number, query: string): Promise<DirectoryPage<DirectoryMember>>;
  createUser(username: string, displayName: string, email: string, phoneNumber: string, password: string): Promise<DirectoryUser>;
  updateUser(id: string, displayName: string, email: string, phoneNumber: string): Promise<DirectoryUser>;
  createTenant(requestId: string, name: string, administratorAccountId: string): Promise<DirectoryTenant>;
  updateTenant(id: string, name: string): Promise<DirectoryTenant>;
  addMember(tenant: string, account: string): Promise<unknown>;
  /** 签发绑定已有账号的 24 小时邀请，未接受前不创建成员，凭证只返回一次。 */
  inviteMember(tenant: string, account: string): Promise<{ invitation: { id: string }; token: string }>;
  userState(id: string, enabled: boolean): Promise<unknown>;
  /** 强制撤销目标账号全部登录；服务端复核平台管理权，不能用于本人注销。 */
  revokeUserSessions(id: string): Promise<unknown>;
  /** 为普通有效账号签发 10 分钟单次重置凭证；原文只显示一次，管理员需核实交付对象。 */
  issuePasswordRecovery(id: string): Promise<{ token: string }>;
  /** 当前租户固定集成主体目录，不返回密钥。 */
  clients(tenant: string): Promise<IntegrationClient[]>;
  /** 创建时只申请已实现 scope：字典读取或显式业务引用登记；密钥仅显示一次。 */
  createClient(tenant: string, name: string, scopes: string[]): Promise<IntegrationSecret>;
  /** 状态更改推进授权版本并撤销全部旧 Token。 */
  clientState(client: IntegrationClient, enabled: boolean): Promise<IntegrationClient>;
  /** 轮换立即撤销旧密钥和旧 Token，版本冲突不覆盖。 */
  rotateClient(client: IntegrationClient): Promise<IntegrationSecret>;
  tenantState(id: string, enabled: boolean): Promise<unknown>;
  memberState(tenant: string, member: string, enabled: boolean): Promise<unknown>;
  removeMember(tenant: string, member: string): Promise<unknown>;
}
