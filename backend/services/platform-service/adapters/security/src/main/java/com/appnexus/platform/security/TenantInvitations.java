package com.appnexus.platform.security;

import com.appnexus.platform.tenant.PlatformAdministration;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

/** 已有账号邀请/激活闭环；邀请不能自行注册账号、信任未验证邮箱或继承旧成员授权。 */
@Service
public class TenantInvitations {
    private final TenantInvitationsSqlMapper statements;

    
    private final PlatformAdministration platform;
    public TenantInvitations(PlatformAdministration platform,TenantInvitationsSqlMapper statements){this.statements=statements;this.platform=platform;}
    /** 公开管理摘要不返回凭证摘要、账号联系方式或权限信息。 */
    public record View(UUID id,UUID tenantId,UUID accountId,String status,Instant expiresAt){}
    /** 随机邀请凭证仅返回一次，必须通过已核实对象的受控渠道传递。 */
    public record Created(View invitation,String token){@Override public String toString(){return "Created[redacted]";}}
    /** 接受结果保留成员 UUID，重新加入不产生第二个身份。 */
    public record Accepted(UUID tenantId,UUID memberId){}
    /**
     * 平台管理员邀请已有有效账号，不创建成员；同一对象新邀请撤销旧待接受邀请。
     * @param actor 权威平台账号，事务内加锁复核
     * @param tenant 启用租户 UUID
     * @param account 已有启用账号 UUID；未验证联系方式不作为证明
     * @return 24 小时有效凭证及摘要，Token 不写日志
     */
    @Transactional
    public Created invite(UUID actor,UUID tenant,UUID account){
        if(actor==null||tenant==null||account==null)throw invalid();
        lockActor(actor);lockTenant(tenant);lockAccount(account);
        var existing=statements.statement1(tenant.toString(),account.toString());
        if(!existing.isEmpty())throw problem(HttpStatus.CONFLICT,"MEMBERSHIP_EXISTS","账号已有成员身份");
        statements.statement2(tenant.toString(),account.toString());
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);UUID id=UUID.randomUUID();Instant expires=Instant.now().plusSeconds(86400);
        statements.statement3(id.toString(),tenant.toString(),account.toString(),hash(token),java.sql.Timestamp.from(expires),actor.toString());
        audit(actor,tenant,id,"MEMBER_INVITED");return new Created(new View(id,tenant,account,"PENDING",expires),token);
    }
    /** 读取当前租户最近 100 条邀请，平台管理权与账号身份分离。 */
    public List<View> list(UUID actor,UUID tenant){require(actor);return statements.summaries(tenant.toString()).stream().map(row->new View(UUID.fromString((String)row.get("id")),tenant,UUID.fromString((String)row.get("account")),(String)row.get("status"),((java.sql.Timestamp)row.get("expires_at")).toInstant())).toList();}
    /**
     * 本人接受邀请；账号、租户、邀请固定锁顺序，CAS 状态更新与成员创建/复用同事务。
     * @param account 服务端登录主体，不能从请求体指定
     * @param id 邀请 UUID
     * @param token 创建时单次返回的 64 位十六进制凭证，过期/错账号/不存在统一拒绝
     * @return 成员身份，不隐式授予角色或恢复历史授权
     */
    @Transactional
    public Accepted accept(UUID account,UUID id,String token){
        if(account==null||id==null||token==null||!token.matches("[a-f0-9]{64}"))throw invalid();
        var initial=statements.statement4(id.toString(),account.toString());
        if(initial.size()!=1)throw invalid();UUID tenant=UUID.fromString((String)initial.getFirst().get("tenant"));
        lockTenant(tenant);lockAccount(account);
        var rows=statements.statement5(id.toString(),tenant.toString(),account.toString());
        if(rows.size()!=1||!MessageDigest.isEqual((byte[])rows.getFirst().get("token_hash"),hash(token)))throw invalid();
        var members=statements.statement6(tenant.toString(),account.toString());UUID member;
        if(members.isEmpty()){
            member=UUID.randomUUID();statements.statement7(member.toString(),tenant.toString(),account.toString());
        }else{
            var previous=members.getFirst();if(previous.get("removed_at")==null)throw problem(HttpStatus.CONFLICT,"MEMBERSHIP_EXISTS","账号已有成员身份");
            member=UUID.fromString((String)previous.get("id"));
            // 移除流程已撤销授权；再次清除角色快照防御历史错误数据，不能靠重新加入恢复旧授权。
            statements.statement8(tenant.toString(),member.toString());
            statements.statement9(tenant.toString(),member.toString());
        }
        statements.statement10(id.toString());audit(account,tenant,member,"INVITATION_ACCEPTED");return new Accepted(tenant,member);
    }
    /** 平台撤销待接受邀请，已接受或不存在返回冲突，不删除历史。 */
    @Transactional public void revoke(UUID actor,UUID tenant,UUID id){lockActor(actor);lockTenant(tenant);if(statements.statement11(id.toString(),tenant.toString())!=1)throw invalid();audit(actor,tenant,id,"INVITATION_REVOKED");}
    private void lockActor(UUID actor){require(actor);if(statements.statement12(actor.toString()).isEmpty())throw invalid();}
    private void lockTenant(UUID tenant){if(statements.statement13(tenant.toString()).isEmpty())throw invalid();}
    private void lockAccount(UUID account){if(statements.statement14(account.toString()).isEmpty())throw invalid();}
    private void require(UUID actor){if(!platform.isAdministrator(actor))throw problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
    private void audit(UUID actor,UUID tenant,UUID target,String action){statements.statement15(actor.toString(),tenant.toString(),target.toString(),action);}
    private byte[] hash(String token){try{return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private org.springframework.web.ErrorResponseException invalid(){return problem(HttpStatus.BAD_REQUEST,"INVITATION_INVALID","邀请不存在、已失效或不属于当前账号");}
    private org.springframework.web.ErrorResponseException problem(HttpStatus status,String code,String text){return AccountSettingsController.problem(status,code,text);}
}
