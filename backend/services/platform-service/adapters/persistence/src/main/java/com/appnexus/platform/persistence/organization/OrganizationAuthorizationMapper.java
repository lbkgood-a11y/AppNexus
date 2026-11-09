package com.appnexus.platform.persistence.organization;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.persistence.PlatformScoped;
/** 仅查询授权存在性，不读取授权人或其他租户的授权详情。 */
@PlatformScoped(tables={"organization_capability_grant","organization_grant_audit","tenant_member","platform_tenant","organization_administrator_bootstrap","identity_account","tenant_member_primary_organization","tenant_organization_unit","platform_administration_audit","access_member_permission","access_permission","access_role","access_role_permission","access_role_data_policy","access_permission_scope_type","access_member_role","access_member_authorization","access_member_authorization_permission","access_effective_tenant_permission"})
@Mapper
public interface OrganizationAuthorizationMapper {
    /** 初始化必须同时锁定有效账号和成员，禁止为已停用账号建立首个管理授权。 */
    @Select("SELECT BIN_TO_UUID(m.id) FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
    String lockBootstrapMember(@Param("tenant") String tenant,@Param("member") String member);
    /** 仅返回当前租户有效成员的必要展示资料；调用层先检查 GRANT。 */
    @Select("SELECT BIN_TO_UUID(m.id) member_id,a.display_name,BIN_TO_UUID(o.id) organization_id,o.name organization_name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id LEFT JOIN tenant_member_primary_organization p ON p.tenant_id=m.tenant_id AND p.member_id=m.id LEFT JOIN tenant_organization_unit o ON o.tenant_id=p.tenant_id AND o.id=p.organization_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.enabled=TRUE AND a.enabled=TRUE ORDER BY a.display_name,m.id")
    java.util.List<java.util.Map<String,Object>> members(String tenant);
    /** 目标组织在同一租户锁内校验，不允许通过组织 UUID 建立跨租户关联。 */
    @Select("SELECT BIN_TO_UUID(id) FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{organization}) FOR UPDATE")
    String lockOrganization(@Param("tenant") String tenant,@Param("organization") String organization);
    /** 删除前在租户行锁下检查归属；有成员归属时拒绝删除，避免产生悬空引用。 */
    @Select("SELECT EXISTS(SELECT 1 FROM tenant_member_primary_organization WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND organization_id=UUID_TO_BIN(#{organization}))")
    boolean organizationAssigned(@Param("tenant") String tenant,@Param("organization") String organization);
    @Insert("INSERT INTO tenant_member_primary_organization(tenant_id,member_id,organization_id) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{organization})) ON DUPLICATE KEY UPDATE organization_id=VALUES(organization_id)")
    void assignOrganization(@Param("tenant") String tenant,@Param("member") String member,@Param("organization") String organization);
    @Delete("DELETE FROM tenant_member_primary_organization WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member})")
    void clearOrganization(@Param("tenant") String tenant,@Param("member") String member);
    /** 组织归属变更使用平台格式的账号审计；操作者账号由已锁定的租户成员反查，不信任客户端。 */
    @Insert("INSERT INTO platform_administration_audit(actor_account_id,action,target_id,tenant_id) SELECT account_id,'MEMBER_ORGANIZATION_CHANGED',UUID_TO_BIN(#{target}),tenant_id FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{actor})")
    void auditOrganization(@Param("tenant") String tenant,@Param("actor") String actor,@Param("target") String target);
    /** 租户行锁内读取一次性标记；标记永久保留，不因撤权重新初始化。 */
    @Select("SELECT BIN_TO_UUID(member_id) FROM organization_administrator_bootstrap WHERE tenant_id=UUID_TO_BIN(#{tenant})")
    String bootstrapMember(String tenant);
    /** 与授权及审计同事务写入；租户主键防止多实例重复初始化。 */
    @Insert("INSERT INTO organization_administrator_bootstrap(tenant_id,member_id) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}))")
    int claimBootstrap(@Param("tenant") String tenant,@Param("member") String member);
    @Select("SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code='sys_tenant_admin' AND deleted_at IS NULL FOR UPDATE")
    String tenantAdministratorRole(String tenant);
    @Insert("INSERT INTO access_role(id,scope_type,tenant_id,role_type,system_key,code,name,description,status,sort_order) VALUES(UUID_TO_BIN(#{role}),'TENANT',UUID_TO_BIN(#{tenant}),'SYSTEM',CONCAT('TENANT_ADMIN:',#{tenant}),'sys_tenant_admin','租户管理员','租户受控初始化角色；拥有全部已发布租户权限。','ENABLED',0)")
    int createTenantAdministratorRole(@Param("tenant") String tenant,@Param("role") String role);
    @Insert("INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT UUID_TO_BIN(#{role}),id,'TENANT',UUID_TO_BIN(#{member}) FROM access_permission WHERE scope_type='TENANT' AND enabled=TRUE")
    int grantTenantAdministratorPermissions(@Param("role") String role,@Param("member") String member);
    @Insert("INSERT IGNORE INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by) SELECT rp.role_id,rp.permission_id,'ALL',st.resource_type,UUID_TO_BIN(#{member}) FROM access_role_permission rp JOIN access_permission_scope_type st ON st.permission_id=rp.permission_id AND st.scope_type='ALL' WHERE rp.role_id=UUID_TO_BIN(#{role})")
    int grantTenantAdministratorDataPolicies(@Param("role") String role,@Param("member") String member);
    @Insert("INSERT IGNORE INTO access_member_role(tenant_id,member_id,role_id,assigned_by) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),UUID_TO_BIN(#{member}))")
    int assignTenantAdministratorRole(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
    /** 初始化前要求该租户从未存在管理授权，避免覆盖人工运维治理的管理者。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_effective_tenant_permission WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND permission_code='organization.permission:grant')")
    boolean hasManagementHistory(String tenant);
    /** 统一 RBAC 双读分支；所有连接固定 tenant_id，停用主体、角色或权限均不生效。 */
    @Select("SELECT EXISTS(SELECT 1 FROM tenant_member m JOIN identity_account a ON a.id=m.account_id JOIN platform_tenant t ON t.id=m.tenant_id JOIN access_member_role mr ON mr.tenant_id=m.tenant_id AND mr.member_id=m.id JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=m.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id AND ap.permission_id=p.id WHERE (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) AND m.tenant_id=UUID_TO_BIN(#{tenantId}) AND m.id=UUID_TO_BIN(#{memberId}) AND m.enabled=TRUE AND a.enabled=TRUE AND t.enabled=TRUE AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE AND p.code=CASE WHEN #{capability}='GRANT' THEN 'organization.permission:grant' ELSE CONCAT('organization.unit:',LOWER(#{capability})) END)")
    boolean rbacExists(@Param("tenantId") String tenantId,@Param("memberId") String memberId,@Param("capability") String capability);

    /** 为兼容组织授权入口建立一条独立统一授权分支，不写旧授权表。 */
    @Insert("INSERT IGNORE INTO access_role(id,scope_type,tenant_id,role_type,system_key,code,name,description,status,sort_order,created_by) SELECT UUID_TO_BIN(UUID()),'TENANT',UUID_TO_BIN(#{tenant}),'SYSTEM',CONCAT('LEGACY:',LEFT(SHA2(CONCAT(UPPER(REPLACE(#{tenant},'-','')),UPPER(REPLACE(#{member},'-','')),p.code),256),56)),CONCAT('sys_legacy_',LEFT(SHA2(CONCAT(UPPER(REPLACE(#{member},'-','')),p.code),256),32)),'旧组织授权迁移','由兼容组织授权入口建立；不得手工分配或扩权。','ENABLED',9999,UUID_TO_BIN(#{actor}) FROM access_permission p WHERE p.code=CASE WHEN #{capability}='GRANT' THEN 'organization.permission:grant' ELSE CONCAT('organization.unit:',LOWER(#{capability})) END")
    int ensureCompatibilityRole(@Param("tenant") String tenant,@Param("member") String member,@Param("capability") String capability,@Param("actor") String actor);
    @Select("SELECT BIN_TO_UUID(r.id) FROM access_role r JOIN access_permission p ON p.code=CASE WHEN #{capability}='GRANT' THEN 'organization.permission:grant' ELSE CONCAT('organization.unit:',LOWER(#{capability})) END WHERE r.system_key=CONCAT('LEGACY:',LEFT(SHA2(CONCAT(UPPER(REPLACE(#{tenant},'-','')),UPPER(REPLACE(#{member},'-','')),p.code),256),56)) FOR UPDATE")
    String compatibilityRole(@Param("tenant") String tenant,@Param("member") String member,@Param("capability") String capability);
    @Insert("INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT UUID_TO_BIN(#{role}),id,'TENANT',UUID_TO_BIN(#{actor}) FROM access_permission WHERE code=CASE WHEN #{capability}='GRANT' THEN 'organization.permission:grant' ELSE CONCAT('organization.unit:',LOWER(#{capability})) END")
    int ensureCompatibilityPermission(@Param("role") String role,@Param("capability") String capability,@Param("actor") String actor);
    @Insert("INSERT IGNORE INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by) SELECT UUID_TO_BIN(#{role}),id,'ALL','organization.unit',UUID_TO_BIN(#{actor}) FROM access_permission WHERE resource='organization.unit' AND code=CONCAT('organization.unit:',LOWER(#{capability}))")
    int ensureCompatibilityPolicy(@Param("role") String role,@Param("capability") String capability,@Param("actor") String actor);
    @Insert("INSERT IGNORE INTO access_member_role(tenant_id,member_id,role_id,assigned_by) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),UUID_TO_BIN(#{actor}))")
    int ensureCompatibilityAssignment(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role,@Param("actor") String actor);
    @Insert("INSERT INTO access_member_authorization(tenant_id,member_id,role_id,role_version,expires_at) SELECT UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),version,#{expires} FROM access_role WHERE id=UUID_TO_BIN(#{role}) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at),role_version=VALUES(role_version)")
    int upsertCompatibilityAuthorization(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role,@Param("expires") java.sql.Timestamp expires);
    @Insert("INSERT IGNORE INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type) SELECT UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),id,CASE WHEN resource='organization.unit' THEN 'ALL' ELSE NULL END,CASE WHEN resource='organization.unit' THEN 'organization.unit' ELSE NULL END FROM access_permission WHERE code=CASE WHEN #{capability}='GRANT' THEN 'organization.permission:grant' ELSE CONCAT('organization.unit:',LOWER(#{capability})) END")
    int ensureCompatibilitySnapshot(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role,@Param("capability") String capability);
    @Delete("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND role_id=UUID_TO_BIN(#{role})")
    int revokeCompatibilityAssignment(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);

    /** 管理页面从统一授权快照读取组织能力；同一能力多来源时只展示一条有效结果。 */
    @Select("SELECT BIN_TO_UUID(mr.member_id) member_id,UPPER(CASE WHEN p.code='organization.permission:grant' THEN 'GRANT' ELSE p.action END) capability,BIN_TO_UUID(MIN(mr.assigned_by)) granted_by,MIN(mr.assigned_at) granted_at,MAX(g.expires_at) expires_at FROM access_member_role mr JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id JOIN access_role_permission rp ON rp.role_id=r.id JOIN access_permission p ON p.id=rp.permission_id JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id AND ap.permission_id=p.id WHERE mr.tenant_id=UUID_TO_BIN(#{tenant}) AND mr.member_id=UUID_TO_BIN(#{member}) AND (p.code LIKE 'organization.unit:%' OR p.code='organization.permission:grant') AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6)) GROUP BY mr.member_id,capability ORDER BY capability")
    java.util.List<java.util.Map<String,Object>> unifiedList(@Param("tenant") String tenant,@Param("member") String member);
    /** 授权审计只追加，不向页面返回操作者以外的敏感信息。 */
    @Insert("INSERT INTO organization_grant_audit(tenant_id,target_member_id,actor_member_id,capability,action) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{target}),UUID_TO_BIN(#{actor}),#{capability},#{action})")
    int audit(@Param("tenant") String tenant,@Param("target") String target,@Param("actor") String actor,@Param("capability") String capability,@Param("action") String action);

    /** 固定先锁租户、再按成员 ID 排序锁成员，和管理授权行；锁持有到事务提交。 */
    @Select("SELECT enabled FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) FOR UPDATE")
    Boolean lockTenant(String tenant);
    @Select("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN(UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{target})) AND enabled=TRUE ORDER BY id FOR UPDATE")
    java.util.List<String> lockMembers(@Param("tenant") String tenant,@Param("actor") String actor,@Param("target") String target);
    /** FOR UPDATE 使并发撤销管理能力与正在授权串行化，不使用事务外授权快照。 */
    @Select("SELECT e.permission_code FROM tenant_member m JOIN access_effective_tenant_permission e ON e.tenant_id=m.tenant_id AND e.member_id=m.id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{actor}) AND e.permission_code='organization.permission:grant' FOR UPDATE")
    String lockAuthority(@Param("tenant") String tenant,@Param("actor") String actor);
}
