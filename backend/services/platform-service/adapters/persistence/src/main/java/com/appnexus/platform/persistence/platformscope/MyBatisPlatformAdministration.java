package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.tenant.PlatformAdministration;
import com.appnexus.platform.authorization.FieldSecurity;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台开通用例；授权复核、目录写入及追加审计同事务，不读取或修改组织业务数据。 */
@Service
public class MyBatisPlatformAdministration implements PlatformAdministration {
    private final AdministrationMapper mapper;
    private final OrganizationAdministratorBootstrap bootstrap;
    private final FieldProtection protection;
    public MyBatisPlatformAdministration(AdministrationMapper mapper,OrganizationAdministratorBootstrap bootstrap,FieldProtection protection) {
        this.mapper=mapper; this.bootstrap=bootstrap;this.protection=protection;
    }
    public boolean isAdministrator(UUID actor) { return mapper.administrator(actor.toString()); }
    /**
     * 运维显式指定现有有效账号，一次性建立平台身份；不创建账号，不覆盖后续撤权。
     * @param username 登录名，1–100 字符，来自可信部署配置；不写日志
     * @throws IllegalStateException 账号无效或初始化已指向另一账号
     */
    @Transactional
    public void initializeAdministrator(String username) {
        String account=mapper.accountByUsername(username);
        if(account==null) throw new IllegalStateException("Existing active platform administrator account required");
        if(mapper.claim(account)==0) {
            if(!account.equals(mapper.initializedAccount())) throw new IllegalStateException("Platform administrator already initialized for another account");
        } else {
            mapper.initialAdministrator(account);
            mapper.audit(account,"PLATFORM_INITIALIZED",account,null);
        }
        mapper.ensurePlatformSystemRole();mapper.ensurePlatformSystemPermissions(account);mapper.ensurePlatformSystemAssignment(account);
    }
    public Page<User> users(UUID actor,int page,int size,String query) {
        require(actor); validatePage(page,size,query);
        return protection.protect(AccessAuthorization.Subject.platform(actor),new Page<>(mapper.users(query,size,(long)(page-1)*size).stream().map(this::user).toList(),mapper.userCount(query)));
    }
    @Transactional
    public User createUser(UUID actor,UUID id,String username,String name,String email,String phone,String hash) {
        lock(actor);
        if(username==null || !username.matches("[A-Za-z0-9][A-Za-z0-9._@-]{0,99}")) throw new IllegalArgumentException("Invalid username");
        validateName(name);
        if(hash==null || !hash.startsWith("$argon2")) throw new IllegalArgumentException("Argon2 hash required");
        email=normalizeEmail(email);phone=normalizePhone(phone);
        mapper.createUser(id.toString(),username,name,email,phone,hash);
        mapper.audit(actor.toString(),"USER_CREATED",id.toString(),null);
        return user(actor,mapper.user(id.toString()));
    }
    @Transactional
    public User updateUser(UUID actor,UUID target,String name,String email,String phone) {
        lock(actor);validateName(name);
        var restrictions=restrictions(actor);var previous=mapper.user(target.toString());if(previous==null)throw new IllegalArgumentException("Account unavailable");
        // 无原文查看权的联系方式只读，忽略回传展示占位值，禁止把脱敏值写回数据库。
        email=restrictions.containsKey("email")?(String)previous.get("email"):normalizeEmail(email);
        phone=restrictions.containsKey("phoneNumber")?(String)previous.get("phone_number"):normalizePhone(phone);
        if(mapper.updateUser(target.toString(),name,email,phone)==0) throw new IllegalArgumentException("Account unavailable");
        mapper.audit(actor.toString(),"USER_PROFILE_UPDATED",target.toString(),null);
        return user(actor,mapper.user(target.toString()));
    }
    @Transactional
    public void setUserEnabled(UUID actor,UUID target,boolean enabled) {
        lock(actor);
        if(actor.equals(target) || mapper.protectedAccount(target.toString())) throw new IllegalArgumentException("Platform administrators are protected");
        // 普通账号可能是租户管理者，平台账号管理也不能绕过管理能力的运维治理。
        if(!enabled && mapper.organizationAdministrator(target.toString())) throw new IllegalArgumentException("Organization administrator requires operations governance");
        if(!enabled && mapper.lockAccount(target.toString())==null) return;
        if(mapper.userState(target.toString(),enabled)==0) throw new IllegalArgumentException("Account unavailable or unchanged");
        mapper.audit(actor.toString(),enabled?"USER_ENABLED":"USER_DISABLED",target.toString(),null);
    }
    public Page<Tenant> tenants(UUID actor,int page,int size,String query) {
        require(actor);validatePage(page,size,query);
        return new Page<>(mapper.tenants(query,size,(long)(page-1)*size).stream().map(r -> new Tenant(id(r,"id"),(String)r.get("name"),enabled(r))).toList(),mapper.tenantCount(query));
    }
    @Transactional
    public Tenant createTenant(UUID actor,UUID request,String name,UUID administrator) {
        lock(actor); name=normalizedName(name);
        var prior=mapper.creation(request.toString());
        if(prior!=null) {
            if(!actor.toString().equals(prior.get("actor")) || !administrator.toString().equals(prior.get("administrator")) || !name.equals(prior.get("tenant_name")))
                throw new IllegalArgumentException("Idempotency request parameters differ");
            return new Tenant(id(prior,"tenant_id"),name,true);
        }
        if(mapper.lockAccount(administrator.toString())==null) throw new IllegalArgumentException("Active administrator account required");
        UUID tenant=com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate(),member=com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
        mapper.createTenant(tenant.toString(),name);
        mapper.addMember(member.toString(),tenant.toString(),administrator.toString());
        bootstrap.initialize(tenant,member);
        mapper.rememberCreation(request.toString(),tenant.toString(),actor.toString(),administrator.toString(),name);
        mapper.audit(actor.toString(),"TENANT_CREATED",tenant.toString(),tenant.toString());
        return new Tenant(tenant,name,true);
    }
    @Transactional
    public Tenant updateTenant(UUID actor,UUID tenant,String name) {
        lock(actor); name=normalizedName(name);
        if(mapper.lockTenant(tenant.toString())==null) throw new IllegalArgumentException("Tenant unavailable");
        if(mapper.updateTenant(tenant.toString(),name)>0)
            mapper.audit(actor.toString(),"TENANT_UPDATED",tenant.toString(),tenant.toString());
        var updated=mapper.tenant(tenant.toString());
        return new Tenant(id(updated,"id"),(String)updated.get("name"),enabled(updated));
    }
    @Transactional
    public void setTenantEnabled(UUID actor,UUID tenant,boolean enabled) {
        lock(actor);Boolean previous=mapper.lockTenant(tenant.toString());
        if(previous==null) throw new IllegalArgumentException("Tenant unavailable");
        if(previous==enabled)return;
        mapper.tenantState(tenant.toString(),enabled);
        mapper.audit(actor.toString(),enabled?"TENANT_ENABLED":"TENANT_DISABLED",tenant.toString(),tenant.toString());
    }
    public Page<Member> members(UUID actor,UUID tenant,int page,int size,String query) {
        require(actor);validatePage(page,size,query);
        return new Page<>(mapper.members(tenant.toString(),query,size,(long)(page-1)*size).stream().map(r -> new Member(id(r,"member_id"),id(r,"account_id"),(String)r.get("username"),(String)r.get("display_name"),enabled(r),r.get("removed_at")!=null)).toList(),mapper.memberCount(tenant.toString(),query));
    }
    @Transactional
    public void addMember(UUID actor,UUID tenant,UUID account) {
        lock(actor); activeTenant(tenant);
        if(mapper.lockAccount(account.toString())==null) throw new IllegalArgumentException("Active account required");
        var existing=mapper.lockMemberByAccount(tenant.toString(),account.toString());
        if(existing!=null) {
            if(existing.get("removed_at")==null)throw new IllegalArgumentException("Membership already exists");
            String member=(String)existing.get("member_id");
            if(mapper.rejoinMember(tenant.toString(),member)!=1)throw new IllegalArgumentException("Membership changed");
            mapper.audit(actor.toString(),"MEMBER_REJOINED",member,tenant.toString());return;
        }
        UUID member=com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
        mapper.addMember(member.toString(),tenant.toString(),account.toString());
        mapper.audit(actor.toString(),"MEMBER_ADDED",member.toString(),tenant.toString());
    }
    @Transactional
    public void removeMember(UUID actor,UUID tenant,UUID member) {
        lock(actor);activeTenant(tenant);
        var previous=mapper.lockMember(tenant.toString(),member.toString());
        if(previous==null)throw new IllegalArgumentException("Member unavailable");
        if(mapper.manages(tenant.toString(),member.toString())&&mapper.activeManagerCount(tenant.toString())<=1)
            throw new IllegalArgumentException("Last active administrator cannot be removed");
        mapper.removeMemberLeaders(tenant.toString(),member.toString());
        mapper.endMemberAssignments(tenant.toString(),member.toString());
        mapper.revokeMember(tenant.toString(),member.toString());
        mapper.revokeMemberRoles(tenant.toString(),member.toString());
        mapper.revokeMemberPermissions(tenant.toString(),member.toString());
        if(mapper.removeMember(tenant.toString(),member.toString())!=1)throw new IllegalArgumentException("Member already removed");
        mapper.audit(actor.toString(),"MEMBER_REMOVED",member.toString(),tenant.toString());
    }
    @Transactional
    public void setMemberEnabled(UUID actor,UUID tenant,UUID member,boolean enabled) {
        lock(actor); activeTenant(tenant);
        var previous=mapper.lockMember(tenant.toString(),member.toString());
        if(previous==null) throw new IllegalArgumentException("Member unavailable");
        if(Boolean.TRUE.equals(mapper.memberRemoved(tenant.toString(),member.toString())))
            throw new IllegalArgumentException("Removed member must rejoin through membership creation");
        if(!enabled && mapper.manages(tenant.toString(),member.toString()))
            throw new IllegalArgumentException("Administrator membership requires operations governance");
        if(previous==enabled) return;
        mapper.memberState(tenant.toString(),member.toString(),enabled);
        if(!enabled) {
            mapper.removeMemberLeaders(tenant.toString(),member.toString());
            mapper.endMemberAssignments(tenant.toString(),member.toString());
            mapper.revokeMember(tenant.toString(),member.toString());
            mapper.revokeMemberRoles(tenant.toString(),member.toString());
            mapper.revokeMemberPermissions(tenant.toString(),member.toString());
            mapper.bumpMemberAuthority(tenant.toString(),member.toString());
        }
        mapper.audit(actor.toString(),enabled?"MEMBER_ENABLED":"MEMBER_DISABLED",member.toString(),tenant.toString());
    }
    /** 读请求逐次校验；异常默认拒绝，不用用户名硬编码平台角色。 */
    private void require(UUID actor) { if(!isAdministrator(actor)) throw new SecurityException("Platform authority required"); }
    private void lock(UUID actor) { if(mapper.lockAdministrator(actor.toString())==null) throw new SecurityException("Platform authority required"); }
    private void activeTenant(UUID tenant) { if(!Boolean.TRUE.equals(mapper.lockTenant(tenant.toString()))) throw new IllegalArgumentException("Active tenant required"); }
    private void validateName(String name) { if(name==null || name.isBlank() || name.length()>100) throw new IllegalArgumentException("Name must contain 1–100 characters"); }
    private String normalizedName(String name) {
        if(name==null)throw new IllegalArgumentException("Name required");
        String normalized=name.strip();validateName(normalized);return normalized;
    }
    /** 邮箱归一化为小写并限制基本结构；完整可达性由后续验证流程确认。 */
    private String normalizeEmail(String value) {
        if(value==null || value.isBlank()) return null;
        String email=value.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Invalid email");
        return email;
    }
    /** 手机号统一使用 E.164，不在服务端猜测国家码或删除本地号码前缀。 */
    private String normalizePhone(String value) {
        if(value==null || value.isBlank()) return null;
        String phone=value.trim();
        if(!phone.matches("^\\+[1-9][0-9]{7,14}$")) throw new IllegalArgumentException("Invalid E.164 phone number");
        return phone;
    }
    private void validatePage(int page,int size,String query) { if(page<1 || size<1 || size>100 || query==null || query.length()>100) throw new IllegalArgumentException("Invalid page or query"); }
    private UUID id(Map<String,Object> row,String key) { return UUID.fromString((String)row.get(key)); }
    private boolean enabled(Map<String,Object> row) { return Boolean.TRUE.equals(row.get("enabled")) || row.get("enabled") instanceof Number n && n.intValue()!=0; }
    private boolean flag(Map<String,Object> row,String key) { return Boolean.TRUE.equals(row.get(key)) || row.get(key) instanceof Number n && n.intValue()!=0; }
    /** 单项返回复用集中字段投影，业务层不包含字段脱敏算法。 */
    private User user(UUID actor,Map<String,Object> row){return protection.protect(AccessAuthorization.Subject.platform(actor),user(row));}
    private User user(Map<String,Object> row) {
        if(row==null)throw new IllegalArgumentException("Account unavailable");
        return new User(id(row,"id"),(String)row.get("username"),(String)row.get("display_name"),
            (String)row.get("email"),flag(row,"email_verified"),(String)row.get("phone_number"),flag(row,"phone_verified"),enabled(row));
    }
    private Map<String,FieldSecurity.Mode> restrictions(UUID actor){return protection.restrictions(AccessAuthorization.Subject.platform(actor),"identity.account");}
}
