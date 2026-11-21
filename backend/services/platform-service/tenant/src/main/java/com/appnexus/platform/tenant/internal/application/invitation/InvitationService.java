package com.appnexus.platform.tenant.internal.application.invitation;
import com.appnexus.platform.tenant.TenantInvitations;
import com.appnexus.platform.tenant.TenantInvitations.*;
import com.appnexus.platform.tenant.port.*;

import com.appnexus.platform.tenant.PlatformAdministration;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;


/** 已有账号邀请/激活闭环；邀请不能自行注册账号、信任未验证邮箱或继承旧成员授权。 */
public final class InvitationService implements TenantInvitations {
    private final InvitationRecords statements;
    private final InvitationTransaction transaction;
    private final InvitationFieldAccess fields;

    
    private final PlatformAdministration platform;
    /** @param fields 经平台授权装配的实时字段策略，禁止缺省放行 */
    public InvitationService(PlatformAdministration platform,InvitationRecords statements,InvitationTransaction transaction,InvitationFieldAccess fields){this.transaction=transaction;this.statements=statements;this.platform=platform;this.fields=Objects.requireNonNull(fields);}
    /**
     * 平台管理员邀请已有有效账号，不创建成员；同一对象新邀请撤销旧待接受邀请。
     * @param actor 权威平台账号，事务内加锁复核
     * @param tenant 启用租户 UUID
     * @param account 已有启用账号 UUID；未验证联系方式不作为证明
     * @return 24 小时有效凭证及摘要，Token 不写日志
     */
    
    public Created invite(UUID actor,UUID tenant,UUID account){return transaction.required(()->{
        if(actor==null||tenant==null||account==null)throw invalid();
        lockActor(actor);
        if(fields.hideInvitee(actor))throw problem(403,"DATA_FIELD_WRITE_DENIED","受邀账号受字段限制");
        lockTenant(tenant);lockAccount(account);
        var existing=statements.existingMembership(tenant.toString(),account.toString());
        if(!existing.isEmpty())throw problem(409,"MEMBERSHIP_EXISTS","账号已有成员身份");
        statements.revokePending(tenant.toString(),account.toString());
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);UUID id=UUID.randomUUID();Instant expires=Instant.now().plusSeconds(86400);
        statements.createInvitation(id.toString(),tenant.toString(),account.toString(),hash(token),expires,actor.toString());
        audit(actor,tenant,id,"MEMBER_INVITED");return new Created(new View(id,tenant,account,"PENDING",expires),token);
    });}
    /** 读取当前租户最近 100 条邀请，平台管理权与账号身份分离。 */
    public List<View> list(UUID actor,UUID tenant){
        require(actor);
        boolean hideInvitee=fields.hideInvitee(actor);
        return statements.summaries(tenant.toString()).stream().map(row->new View(
            UUID.fromString((String)row.get("id")),tenant,hideInvitee?null:UUID.fromString((String)row.get("account")),
            (String)row.get("status"),(Instant)row.get("expires_at"))).toList();
    }
    /**
     * 本人接受邀请；账号、租户、邀请固定锁顺序，CAS 状态更新与成员创建/复用同事务。
     * @param account 服务端登录主体，不能从请求体指定
     * @param id 邀请 UUID
     * @param token 创建时单次返回的 64 位十六进制凭证，过期/错账号/不存在统一拒绝
     * @return 成员身份，不隐式授予角色或恢复历史授权
     */
    
    public Accepted accept(UUID account,UUID id,String token){return transaction.required(()->{
        if(account==null||id==null||token==null||!token.matches("[a-f0-9]{64}"))throw invalid();
        var initial=statements.invitationOwner(id.toString(),account.toString());
        if(initial.size()!=1)throw invalid();UUID tenant=UUID.fromString((String)initial.getFirst().get("tenant"));
        lockTenant(tenant);lockAccount(account);
        var rows=statements.lockPendingInvitation(id.toString(),tenant.toString(),account.toString());
        if(rows.size()!=1||!MessageDigest.isEqual((byte[])rows.getFirst().get("token_hash"),hash(token)))throw invalid();
        var members=statements.lockMembership(tenant.toString(),account.toString());UUID member;
        if(members.isEmpty()){
            member=UUID.randomUUID();statements.createMembership(member.toString(),tenant.toString(),account.toString());
        }else{
            var previous=members.getFirst();if(previous.get("removed_at")==null)throw problem(409,"MEMBERSHIP_EXISTS","账号已有成员身份");
            member=UUID.fromString((String)previous.get("id"));
            // 移除流程已撤销授权；再次清除角色快照防御历史错误数据，不能靠重新加入恢复旧授权。
            statements.clearMemberRoles(tenant.toString(),member.toString());
            statements.reactivateMembership(tenant.toString(),member.toString());
        }
        statements.acceptInvitation(id.toString());audit(account,tenant,member,"INVITATION_ACCEPTED");return new Accepted(tenant,member);
    });}
    /** 平台撤销待接受邀请，已接受或不存在返回冲突，不删除历史。 */
     public void revoke(UUID actor,UUID tenant,UUID id){transaction.required(()->{lockActor(actor);lockTenant(tenant);if(statements.revokeInvitation(id.toString(),tenant.toString())!=1)throw invalid();audit(actor,tenant,id,"INVITATION_REVOKED");return null;});}
    private void lockActor(UUID actor){require(actor);if(statements.lockAdministrator(actor.toString()).isEmpty())throw invalid();}
    private void lockTenant(UUID tenant){if(statements.lockTenant(tenant.toString()).isEmpty())throw invalid();}
    private void lockAccount(UUID account){if(statements.lockAccount(account.toString()).isEmpty())throw invalid();}
    private void require(UUID actor){if(!platform.isAdministrator(actor))throw problem(403,"PLATFORM_DENIED","无平台管理权限");}
    private void audit(UUID actor,UUID tenant,UUID target,String action){statements.appendAudit(actor.toString(),tenant.toString(),target.toString(),action);}
    private byte[] hash(String token){try{return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private Rejection invalid(){return problem(400,"INVITATION_INVALID","邀请不存在、已失效或不属于当前账号");}
    private Rejection problem(int status,String code,String text){return new Rejection(status,code);}
}
