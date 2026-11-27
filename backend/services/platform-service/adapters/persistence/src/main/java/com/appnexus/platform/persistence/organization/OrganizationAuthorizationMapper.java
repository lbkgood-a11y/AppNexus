package com.appnexus.platform.persistence.organization;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.PlatformScoped;
/** 仅查询授权存在性，不读取授权人或其他租户的授权详情。 */
@PlatformScoped(tables={"organization_capability_grant","organization_grant_audit","tenant_member","platform_tenant","organization_administrator_bootstrap","identity_account","tenant_person_organization","tenant_person_position","tenant_organization_position","tenant_organization_unit","platform_administration_audit","access_member_permission","access_permission","access_role","access_role_permission","access_role_data_policy","access_permission_scope_type","access_member_role","access_member_authorization","access_member_authorization_permission","access_effective_tenant_permission"})
@Mapper
public interface OrganizationAuthorizationMapper {
    /** 初始化必须同时锁定有效账号和成员，禁止为已停用账号建立首个管理授权。 */
    @Select("SELECT BIN_TO_UUID(m.id) FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(#{tenant}) AND m.id=UUID_TO_BIN(#{member}) AND m.enabled=TRUE AND a.enabled=TRUE FOR UPDATE")
    String lockBootstrapMember(@Param("tenant") String tenant,@Param("member") String member);
    /** 租户行锁内读取一次性标记；标记永久保留，不因撤权重新初始化。 */
    @Select("SELECT BIN_TO_UUID(member_id) FROM organization_administrator_bootstrap WHERE tenant_id=UUID_TO_BIN(#{tenant})")
    String bootstrapMember(String tenant);
    /** 与授权及审计同事务写入；租户唯一键防止多实例重复初始化。 */
    @Insert("INSERT INTO organization_administrator_bootstrap(tenant_id,member_id) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}))")
    int claimBootstrap(@Param("tenant") String tenant,@Param("member") String member);
    @Select("SELECT BIN_TO_UUID(id) FROM access_role WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND code='sys_tenant_admin' AND deleted_at IS NULL FOR UPDATE")
    String tenantAdministratorRole(String tenant);
    @Insert("INSERT INTO access_role(id,scope_type,tenant_id,role_type,system_key,code,name,description,status,sort_order) VALUES(UUID_TO_BIN(#{role}),'TENANT',UUID_TO_BIN(#{tenant}),'SYSTEM',CONCAT('TENANT_ADMIN:',#{tenant}),'sys_tenant_admin','租户管理员','租户受控初始化角色；授予可自动配置范围的已发布权限。','ENABLED',0)")
    int createTenantAdministratorRole(@Param("tenant") String tenant,@Param("role") String role);
    /** 初始化只授予可完整落地的权限。租户归属取自持锁的父角色，指定记录范围由管理员配置。 */
    @Insert("""
        INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by,tenant_id)
        SELECT r.id,p.id,'TENANT',UUID_TO_BIN(#{member}),r.tenant_id
        FROM access_role r JOIN access_permission p ON r.id=UUID_TO_BIN(#{role})
        WHERE p.scope_type='TENANT' AND p.enabled=TRUE
          AND (NOT EXISTS (SELECT 1 FROM access_permission_scope_type s WHERE s.permission_id=p.id)
            OR EXISTS (SELECT 1 FROM access_permission_scope_type s WHERE s.permission_id=p.id AND s.scope_type='ALL'))
        """)
    int grantTenantAdministratorPermissions(@Param("role") String role,@Param("member") String member);
    /** 在同一初始化事务中为受支持的授权建立全范围策略，并沿用授权关系的租户。 */
    @Insert("INSERT IGNORE INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,tenant_id) SELECT rp.role_id,rp.permission_id,'ALL',st.resource_type,UUID_TO_BIN(#{member}),rp.tenant_id FROM access_role_permission rp JOIN access_permission_scope_type st ON st.permission_id=rp.permission_id AND st.scope_type='ALL' WHERE rp.role_id=UUID_TO_BIN(#{role})")
    int grantTenantAdministratorDataPolicies(@Param("role") String role,@Param("member") String member);
    @Insert("INSERT IGNORE INTO access_member_role(tenant_id,member_id,role_id,assigned_by) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),UUID_TO_BIN(#{role}),UUID_TO_BIN(#{member}))")
    int assignTenantAdministratorRole(@Param("tenant") String tenant,@Param("member") String member,@Param("role") String role);
    /** 初始化前要求该租户从未存在管理授权，避免覆盖人工运维治理的管理者。 */
    @Select("SELECT EXISTS(SELECT 1 FROM access_effective_tenant_permission WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND permission_code='organization.permission:grant')")
    boolean hasManagementHistory(String tenant);
    /** 固定先锁租户、再按成员 ID 排序锁成员，和管理授权行；锁持有到事务提交。 */
    @Select("SELECT enabled FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) FOR UPDATE")
    Boolean lockTenant(String tenant);
}
