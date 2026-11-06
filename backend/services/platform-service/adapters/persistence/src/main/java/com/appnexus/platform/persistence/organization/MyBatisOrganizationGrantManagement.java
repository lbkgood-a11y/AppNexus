package com.appnexus.platform.persistence.organization;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.TenantAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
/** 租户成员组织授权及追加式审计的 MyBatis 适配器。 */
@Repository
public class MyBatisOrganizationGrantManagement implements OrganizationGrantManagement {
    private final OrganizationAuthorizationMapper mapper;
    private final TenantAccess members;
    public MyBatisOrganizationGrantManagement(OrganizationAuthorizationMapper mapper,TenantAccess members) {
        this.mapper=mapper;this.members=members;
    }
    public List<Member> members(UUID tenant) {
        return mapper.members(tenant.toString()).stream().map(row -> new Member(
            UUID.fromString((String)row.get("member_id")), (String)row.get("display_name"),
            row.get("organization_id")==null?null:UUID.fromString((String)row.get("organization_id")),(String)row.get("organization_name"))).toList();
    }
    /** 归属与能力独立；成员及组织必须属于当前租户，写入和审计原子提交。 */
    @Transactional
    public void assignOrganization(UUID tenant,UUID target,UUID actor,UUID organization) {
        lockAndAuthorize(tenant,target,actor);
        if(organization==null) mapper.clearOrganization(tenant.toString(),target.toString());
        else {
            if(mapper.lockOrganization(tenant.toString(),organization.toString())==null) throw new IllegalArgumentException("Organization unavailable");
            mapper.assignOrganization(tenant.toString(),target.toString(),organization.toString());
        }
        mapper.auditOrganization(tenant.toString(),actor.toString(),target.toString());
    }
    public List<Grant> list(UUID tenant,UUID target) {
        requireMember(tenant,target);
        return mapper.list(tenant.toString(),target.toString()).stream().map(row -> new Grant(
            UUID.fromString((String)row.get("member_id")),
            OrganizationCapability.valueOf((String)row.get("capability")),
            UUID.fromString((String)row.get("granted_by")),
            ((java.sql.Timestamp)row.get("granted_at")).toInstant(),
            row.get("expires_at")==null?null:((java.sql.Timestamp)row.get("expires_at")).toInstant())).toList();
    }
    /** 写授权与审计同事务；不可用租户成员身份冒充 GRANT 能力，Controller 在调用前校验。 */
    @Transactional
    public Grant grant(UUID tenant,UUID target,UUID actor,OrganizationCapability capability,Instant expires) {
        lockAndAuthorize(tenant,target,actor);
        if(capability==OrganizationCapability.GRANT) throw new IllegalArgumentException("Grant-management capability is not delegable");
        if(expires!=null && !expires.isAfter(Instant.now())) throw new IllegalArgumentException("Grant expiry must be in the future");
        mapper.grant(tenant.toString(),target.toString(),capability.name(),actor.toString(),expires==null?null:Timestamp.from(expires));
        mapper.audit(tenant.toString(),target.toString(),actor.toString(),capability.name(),"GRANTED");
        return list(tenant,target).stream().filter(g->g.capability()==capability).findFirst().orElseThrow();
    }
    @Transactional
    public boolean revoke(UUID tenant,UUID target,UUID actor,OrganizationCapability capability) {
        lockAndAuthorize(tenant,target,actor);
        if(capability==OrganizationCapability.GRANT) throw new IllegalArgumentException("Grant-management capability is not delegable");
        if(mapper.revoke(tenant.toString(),target.toString(),capability.name(),actor.toString())!=1)return false;
        mapper.audit(tenant.toString(),target.toString(),actor.toString(),capability.name(),"REVOKED");
        return true;
    }
    /** 租户和成员状态与管理能力在事务内锁定，避免校验后到写入前状态发生竞态。 */
    private void lockAndAuthorize(UUID tenant,UUID target,UUID actor) {
        if(!Boolean.TRUE.equals(mapper.lockTenant(tenant.toString()))) throw new IllegalArgumentException("Active tenant required");
        var active=mapper.lockMembers(tenant.toString(),actor.toString(),target.toString());
        if(!active.contains(actor.toString())||!active.contains(target.toString()))
            throw new IllegalArgumentException("Active tenant member required");
        if(mapper.lockAuthority(tenant.toString(),actor.toString())==null)
            throw new SecurityException("Grant authority required");
    }
    private void requireMember(UUID tenant,UUID member) {
        if(!members.isActiveMember(tenant,member)) throw new IllegalArgumentException("Active tenant member required");
    }
}
