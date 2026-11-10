package com.appnexus.platform.security;

import com.appnexus.platform.tenant.PlatformAdministration;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

/** 已有账号邀请/激活闭环；邀请不能自行注册账号、信任未验证邮箱或继承旧成员授权。 */
@Service
public class TenantInvitations {
    private final JdbcTemplate jdbc;
    private final PlatformAdministration platform;
    public TenantInvitations(JdbcTemplate jdbc,PlatformAdministration platform){this.jdbc=jdbc;this.platform=platform;}
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
        var existing=jdbc.queryForList("SELECT id FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?) AND removed_at IS NULL FOR UPDATE",tenant.toString(),account.toString());
        if(!existing.isEmpty())throw problem(HttpStatus.CONFLICT,"MEMBERSHIP_EXISTS","账号已有成员身份");
        jdbc.update("UPDATE tenant_invitation SET status='REVOKED' WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?) AND status='PENDING'",tenant.toString(),account.toString());
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);UUID id=UUID.randomUUID();Instant expires=Instant.now().plusSeconds(86400);
        jdbc.update("INSERT INTO tenant_invitation(id,tenant_id,account_id,token_hash,expires_at,invited_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?))",id.toString(),tenant.toString(),account.toString(),hash(token),java.sql.Timestamp.from(expires),actor.toString());
        audit(actor,tenant,id,"MEMBER_INVITED");return new Created(new View(id,tenant,account,"PENDING",expires),token);
    }
    /** 读取当前租户最近 100 条邀请，平台管理权与账号身份分离。 */
    public List<View> list(UUID actor,UUID tenant){require(actor);return jdbc.query("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(account_id) account,status,expires_at FROM tenant_invitation WHERE tenant_id=UUID_TO_BIN(?) ORDER BY created_at DESC,id LIMIT 100",(row,index)->new View(UUID.fromString(row.getString("id")),tenant,UUID.fromString(row.getString("account")),row.getString("status"),row.getTimestamp("expires_at").toInstant()),tenant.toString());}
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
        var initial=jdbc.queryForList("SELECT BIN_TO_UUID(tenant_id) tenant FROM tenant_invitation WHERE id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",id.toString(),account.toString());
        if(initial.size()!=1)throw invalid();UUID tenant=UUID.fromString((String)initial.getFirst().get("tenant"));
        lockTenant(tenant);lockAccount(account);
        var rows=jdbc.queryForList("SELECT token_hash FROM tenant_invitation WHERE id=UUID_TO_BIN(?) AND tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?) AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE",id.toString(),tenant.toString(),account.toString());
        if(rows.size()!=1||!MessageDigest.isEqual((byte[])rows.getFirst().get("token_hash"),hash(token)))throw invalid();
        var members=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,removed_at FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?) FOR UPDATE",tenant.toString(),account.toString());UUID member;
        if(members.isEmpty()){
            member=UUID.randomUUID();jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member.toString(),tenant.toString(),account.toString());
        }else{
            var previous=members.getFirst();if(previous.get("removed_at")==null)throw problem(HttpStatus.CONFLICT,"MEMBERSHIP_EXISTS","账号已有成员身份");
            member=UUID.fromString((String)previous.get("id"));
            // 移除流程已撤销授权；再次清除角色快照防御历史错误数据，不能靠重新加入恢复旧授权。
            jdbc.update("DELETE FROM access_member_role WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
            jdbc.update("UPDATE tenant_member SET enabled=TRUE,removed_at=NULL,authority_version=authority_version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),member.toString());
        }
        jdbc.update("UPDATE tenant_invitation SET status='ACCEPTED' WHERE id=UUID_TO_BIN(?)",id.toString());audit(account,tenant,member,"INVITATION_ACCEPTED");return new Accepted(tenant,member);
    }
    /** 平台撤销待接受邀请，已接受或不存在返回冲突，不删除历史。 */
    @Transactional public void revoke(UUID actor,UUID tenant,UUID id){lockActor(actor);lockTenant(tenant);if(jdbc.update("UPDATE tenant_invitation SET status='REVOKED' WHERE id=UUID_TO_BIN(?) AND tenant_id=UUID_TO_BIN(?) AND status='PENDING'",id.toString(),tenant.toString())!=1)throw invalid();audit(actor,tenant,id,"INVITATION_REVOKED");}
    private void lockActor(UUID actor){require(actor);if(jdbc.queryForList("SELECT p.account_id FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(?) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE",actor.toString()).isEmpty())throw invalid();}
    private void lockTenant(UUID tenant){if(jdbc.queryForList("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(?) AND enabled=TRUE FOR UPDATE",tenant.toString()).isEmpty())throw invalid();}
    private void lockAccount(UUID account){if(jdbc.queryForList("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(?) AND enabled=TRUE FOR UPDATE",account.toString()).isEmpty())throw invalid();}
    private void require(UUID actor){if(!platform.isAdministrator(actor))throw problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
    private void audit(UUID actor,UUID tenant,UUID target,String action){jdbc.update("INSERT INTO platform_administration_audit(actor_account_id,tenant_id,target_id,action) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?)",actor.toString(),tenant.toString(),target.toString(),action);}
    private byte[] hash(String token){try{return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private org.springframework.web.ErrorResponseException invalid(){return problem(HttpStatus.BAD_REQUEST,"INVITATION_INVALID","邀请不存在、已失效或不属于当前账号");}
    private org.springframework.web.ErrorResponseException problem(HttpStatus status,String code,String text){return AccountSettingsController.problem(status,code,text);}
}
