package com.appnexus.platform.persistence.organization;
import org.apache.ibatis.annotations.*;
import com.appnexus.platform.persistence.PlatformScoped;
/** 仅查询授权存在性，不读取授权人或其他租户的授权详情。 */
@PlatformScoped(tables={"organization_capability_grant","organization_grant_audit","tenant_member","platform_tenant","organization_administrator_bootstrap","identity_account","tenant_member_primary_organization","tenant_organization_unit","platform_administration_audit"})
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
    /** 初始化前要求该租户从未存在管理授权，避免覆盖人工运维治理的管理者。 */
    @Select("SELECT EXISTS(SELECT 1 FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND capability='GRANT')")
    boolean hasManagementHistory(String tenant);
    /** 租户与成员参数均由服务端已验证上下文生成，权限码来自枚举。 */
    @Select("SELECT EXISTS(SELECT 1 FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(#{tenantId}) AND member_id=UUID_TO_BIN(#{memberId}) AND capability=#{capability} AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(6)))")
    boolean exists(@Param("tenantId") String tenantId,@Param("memberId") String memberId,@Param("capability") String capability);

    /** 列表仅用于相同租户；调用层验证调用者具备 GRANT。 */
    @Select("SELECT BIN_TO_UUID(member_id) member_id,capability,BIN_TO_UUID(granted_by) granted_by,granted_at,expires_at FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(6)) ORDER BY capability")
    java.util.List<java.util.Map<String,Object>> list(@Param("tenant") String tenant,@Param("member") String member);
    /** 授权 upsert 只作用于已验证租户和成员，操作者由服务端上下文传入。 */
    @Insert("INSERT INTO organization_capability_grant(tenant_id,member_id,capability,granted_by,expires_at,revoked_at,revoked_by) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{member}),#{capability},UUID_TO_BIN(#{actor}),#{expires},NULL,NULL) ON DUPLICATE KEY UPDATE granted_by=VALUES(granted_by),granted_at=CURRENT_TIMESTAMP(6),expires_at=VALUES(expires_at),revoked_at=NULL,revoked_by=NULL")
    int grant(@Param("tenant") String tenant,@Param("member") String member,@Param("capability") String capability,@Param("actor") String actor,@Param("expires") java.sql.Timestamp expires);
    /** 撤权必须与租户和成员同时匹配，重复撤权不改写历史。 */
    @Update("UPDATE organization_capability_grant SET revoked_at=CURRENT_TIMESTAMP(6),revoked_by=UUID_TO_BIN(#{actor}) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{member}) AND capability=#{capability} AND revoked_at IS NULL")
    int revoke(@Param("tenant") String tenant,@Param("member") String member,@Param("capability") String capability,@Param("actor") String actor);

    /** 授权审计只追加，不向页面返回操作者以外的敏感信息。 */
    @Insert("INSERT INTO organization_grant_audit(tenant_id,target_member_id,actor_member_id,capability,action) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{target}),UUID_TO_BIN(#{actor}),#{capability},#{action})")
    int audit(@Param("tenant") String tenant,@Param("target") String target,@Param("actor") String actor,@Param("capability") String capability,@Param("action") String action);

    /** 固定先锁租户、再按成员 ID 排序锁成员，和管理授权行；锁持有到事务提交。 */
    @Select("SELECT enabled FROM platform_tenant WHERE id=UUID_TO_BIN(#{tenant}) FOR UPDATE")
    Boolean lockTenant(String tenant);
    @Select("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id IN(UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{target})) AND enabled=TRUE ORDER BY id FOR UPDATE")
    java.util.List<String> lockMembers(@Param("tenant") String tenant,@Param("actor") String actor,@Param("target") String target);
    /** FOR UPDATE 使并发撤销管理能力与正在授权串行化，不使用事务外授权快照。 */
    @Select("SELECT capability FROM organization_capability_grant WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND member_id=UUID_TO_BIN(#{actor}) AND capability='GRANT' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(6)) FOR UPDATE")
    String lockAuthority(@Param("tenant") String tenant,@Param("actor") String actor);
}
