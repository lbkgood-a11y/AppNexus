package com.appnexus.masterdata.organization;
import java.util.*;
/** 组织公开目录与人员归属端口；账号仅是租户成员，不直接归属组织，不返回凭据或敏感人员正文。 */
public interface PersonnelDirectory {
 /** 组织选项；停用不允许新建归属。 */
 record Organization(String id,String name,boolean enabled){}
 /** 岗位属于单一组织，不属于账号。 */
 record Position(String id,String organizationId,String name,boolean enabled){}
 /** 当前租户有效账号成员摘要，不携带组织。 */
 record User(String memberId,String accountId,String name){}
 /** 调用方先验证管理或本人目录准入；用户列表不构成代表关系。 */
 record Catalog(List<Organization> organizations,List<Position> positions,List<User> users){}
 /** 人员组织归属/任职摘要；字段白名单由人员模块校验，日期为 yyyy-MM-dd。 */
 record Affiliation(String id,String section,Map<String,String> values){}
 /** @param tenantId 可信租户，必填
  * @return 同租户组织、岗位及有效账号最小目录 */
 Catalog catalog(UUID tenantId);
 /** @param tenantId 可信租户
  * @param memberId 目标账号成员
  * @return 账号、成员、租户访问仍有效才为 true */
 boolean eligible(UUID tenantId,UUID memberId);
 /** @param tenantId 可信租户
  * @param personId 具体被代表人员，不接受账号成员 ID
  * @param organizationId 具体业务组织
  * @return 人员当前有效组织归属或任职存在；不合并其他人员组织 */
 boolean personInOrganization(UUID tenantId,UUID personId,UUID organizationId);
 /** @return 当前租户人员的唯一权威组织及岗位关系，不读取旧用户关系 */
 List<Affiliation> affiliations(UUID tenantId,UUID personId);
 /** 在调用方已授权且锁定父人员版本的事务中保存，不创建账号组织关系。
  * @param tenantId 可信租户
  * @param personId 同租户人员
  * @param section organizations 或 positions；未知分类拒绝
  * @param id 服务端关系 UUID；编辑时须已属于此人员
  * @param create true 创建，false 编辑，无默认值
  * @param values 固定字段值；引用须同租户启用；主组织修改串行维护
  * @return 是否写入，目标不存在返回 false */
 boolean saveAffiliation(UUID tenantId,UUID personId,String section,UUID id,boolean create,Map<String,String> values);
 /** @return 是否删除同租户人员关系；删除任职时同时解除其负责人引用，不删除账号 */
 boolean deleteAffiliation(UUID tenantId,UUID personId,String section,UUID id);
 /** @return 有效岗位/负责人对应人员；未知 kind 返回空，所有引用限定同租户 */
 List<String> resolvePersons(UUID tenantId,UUID organizationId,String kind,String reference);
 /** 组织和岗位各自使用名称键，避免两个目录的 UUID 互相覆盖。 */
 record OrganizationNameKey(String kind,String id){}
 /** @param context 可信租户主体，必须具有组织目录阅读权限
  * @param references 组织或岗位类型和 UUID，每批最多 200 项
  * @return 已授权的名称 */
 Map<OrganizationNameKey,String> organizationReferenceNames(com.appnexus.platform.tenant.VerifiedTenantContext context,List<OrganizationNameKey> references);
 /** 解析已获准读取的审计账号，复用同租户目录并保留历史成员。
  * @param tenantId 可信租户，必填
  * @param accountIds 响应内去重后的审计账号 UUID
  * @return 按账号 UUID 索引的非空显示名 */
 Map<String,String> accountNames(UUID tenantId,List<String> accountIds);
 /** @param actor 已通过认证的平台账号
  * @param accountIds 授权平台审计结果中的账号 UUID，每批最多 200 项
  * @return 平台审计权限允许的账号显示名 */
 Map<String,String> platformAccountNames(UUID actor,List<String> accountIds);
}
