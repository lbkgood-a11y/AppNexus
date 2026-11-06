package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.tenant.PlatformAdministration;
import com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台开通用例；授权复核、目录写入及追加审计同事务，不读取或修改组织业务数据。 */
@Service
public class MyBatisPlatformAdministration implements PlatformAdministration {
    private final AdministrationMapper mapper;
    private final OrganizationAdministratorBootstrap bootstrap;
    public MyBatisPlatformAdministration(AdministrationMapper mapper,OrganizationAdministratorBootstrap bootstrap) {
        this.mapper=mapper; this.bootstrap=bootstrap;
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
            return;
        }
        mapper.initialAdministrator(account);
        mapper.audit(account,"PLATFORM_INITIALIZED",account,null);
    }
    public Page<User> users(UUID actor,int page,int size,String query) {
        require(actor); validatePage(page,size,query);
        return new Page<>(mapper.users(query,size,(long)(page-1)*size).stream().map(r -> new User(id(r,"id"),(String)r.get("username"),(String)r.get("display_name"),enabled(r))).toList(),mapper.userCount(query));
    }
    @Transactional
    public User createUser(UUID actor,UUID id,String username,String name,String hash) {
        lock(actor);
        if(username==null || !username.matches("[A-Za-z0-9][A-Za-z0-9._@-]{0,99}")) throw new IllegalArgumentException("Invalid username");
        validateName(name);
        if(hash==null || !hash.startsWith("$argon2")) throw new IllegalArgumentException("Argon2 hash required");
        mapper.createUser(id.toString(),username,name,hash);
        mapper.audit(actor.toString(),"USER_CREATED",id.toString(),null);
        return new User(id,username,name,true);
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
        lock(actor); validateName(name);
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
    public void setTenantEnabled(UUID actor,UUID tenant,boolean enabled) {
        lock(actor);if(mapper.lockTenant(tenant.toString())==null) throw new IllegalArgumentException("Tenant unavailable");
        mapper.tenantState(tenant.toString(),enabled);
        mapper.audit(actor.toString(),enabled?"TENANT_ENABLED":"TENANT_DISABLED",tenant.toString(),tenant.toString());
    }
    public Page<Member> members(UUID actor,UUID tenant,int page,int size,String query) {
        require(actor);validatePage(page,size,query);
        return new Page<>(mapper.members(tenant.toString(),query,size,(long)(page-1)*size).stream().map(r -> new Member(id(r,"member_id"),id(r,"account_id"),(String)r.get("username"),(String)r.get("display_name"),enabled(r))).toList(),mapper.memberCount(tenant.toString(),query));
    }
    @Transactional
    public void addMember(UUID actor,UUID tenant,UUID account) {
        lock(actor); activeTenant(tenant);
        if(mapper.lockAccount(account.toString())==null) throw new IllegalArgumentException("Active account required");
        UUID member=com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate();
        mapper.addMember(member.toString(),tenant.toString(),account.toString());
        mapper.audit(actor.toString(),"MEMBER_ADDED",member.toString(),tenant.toString());
    }
    @Transactional
    public void setMemberEnabled(UUID actor,UUID tenant,UUID member,boolean enabled) {
        lock(actor); activeTenant(tenant);
        var previous=mapper.lockMember(tenant.toString(),member.toString());
        if(previous==null) throw new IllegalArgumentException("Member unavailable");
        if(!enabled && mapper.manages(tenant.toString(),member.toString()))
            throw new IllegalArgumentException("Administrator membership requires operations governance");
        if(previous==enabled) return;
        mapper.memberState(tenant.toString(),member.toString(),enabled);
        if(!enabled) mapper.revokeMember(tenant.toString(),member.toString());
        mapper.audit(actor.toString(),enabled?"MEMBER_ENABLED":"MEMBER_DISABLED",member.toString(),tenant.toString());
    }
    /** 读请求逐次校验；异常默认拒绝，不用用户名硬编码平台角色。 */
    private void require(UUID actor) { if(!isAdministrator(actor)) throw new SecurityException("Platform authority required"); }
    private void lock(UUID actor) { if(mapper.lockAdministrator(actor.toString())==null) throw new SecurityException("Platform authority required"); }
    private void activeTenant(UUID tenant) { if(!Boolean.TRUE.equals(mapper.lockTenant(tenant.toString()))) throw new IllegalArgumentException("Active tenant required"); }
    private void validateName(String name) { if(name==null || name.isBlank() || name.length()>100) throw new IllegalArgumentException("Name must contain 1–100 characters"); }
    private void validatePage(int page,int size,String query) { if(page<1 || size<1 || size>100 || query==null || query.length()>100) throw new IllegalArgumentException("Invalid page or query"); }
    private UUID id(Map<String,Object> row,String key) { return UUID.fromString((String)row.get(key)); }
    private boolean enabled(Map<String,Object> row) { return Boolean.TRUE.equals(row.get("enabled")) || row.get("enabled") instanceof Number n && n.intValue()!=0; }
}
