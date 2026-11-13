package com.appnexus.platform.security;

import com.appnexus.platform.tenant.PlatformAdministration;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部机密主体管理与 OAuth 注册编排；SQL 仅访问平台集成元数据，不代替租户业务仓储。
 * 客户端密钥只在创建响应显示一次，数据库保留 Argon2id 哈希；租户绑定创建后不可更改。
 */
@Service
public class IntegrationClients {
    /** 当前正式可执行的外部 API scope；打印代理 scope 只允许受控代理路径，不授予其他租户 API。 */
    public static final Set<String> SUPPORTED=Set.of("dictionaries:read","dictionary-references:write","print-agent:operate","print:submit","document:resolve");
    private final JdbcTemplate jdbc;
    private final RegisteredClientRepository clients;
    private final PasswordEncoder encoder;
    private final PlatformAdministration platform;
    private final ReliableEvents events;
    public IntegrationClients(JdbcTemplate jdbc,RegisteredClientRepository clients,PasswordEncoder encoder,PlatformAdministration platform,ReliableEvents events){
        this.jdbc=jdbc;this.clients=clients;this.encoder=encoder;this.platform=platform;this.events=events;
    }
    /** 管理摘要，不含密钥哈希或 Token；version 用于拒绝并发覆盖。 */
    public record View(String id,UUID tenantId,String name,boolean enabled,int version,Set<String> scopes){}
    /** 只在创建/密钥轮换成功时返回一次，禁止记录对象字符串或客户端持久存储。 */
    public record Created(View client,String secret){@Override public String toString(){return "Created[redacted]";}}
    /** 权威集成状态，不映射为账号或成员。 */
    public record Policy(String id,UUID tenant,int version,boolean enabled){}
    /**
     * @param actor 服务端认证的平台管理操作者
     * @param tenant 要查看的固定租户 UUID
     * @return 最多 100 个客户端，超出时需后续分页扩展，不能无界读取密钥
     */
    public List<View> list(UUID actor,UUID tenant){
        require(actor);
        return jdbc.query("SELECT id,name,enabled,authority_version FROM integration_client WHERE tenant_id=UUID_TO_BIN(?) ORDER BY created_at,id LIMIT 100",
            (row,index)->view(row.getString("id"),tenant,row.getString("name"),row.getBoolean("enabled"),row.getInt("authority_version")),tenant.toString());
    }
    /**
     * 原子创建机密客户端与固定租户绑定；默认 5 分钟不透明 Access Token，无 Refresh Token。
     * @param actor 已认证平台操作者，在事务内锁定权威管理员行
     * @param tenant 已启用租户 UUID，创建后不能换绑
     * @param name 1–100 字符显示名，不接受控制字符
     * @param scopes 非空且仅包含已实现的 scope，仅允许 SUPPORTED 登记的字典、打印或单据解析专用范围
     * @return 一次性密钥和公开摘要
     */
    @Transactional
    public Created create(UUID actor,UUID tenant,String name,Set<String> scopes){
        lock(actor);validate(name,scopes);
        var rows=jdbc.queryForList("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(?) AND enabled=TRUE FOR UPDATE",tenant.toString());
        if(rows.isEmpty())throw invalid();
        String id="partner-"+UUID.randomUUID(),secret=secret();
        clients.save(RegisteredClient.withId(id).clientId(id).clientSecret(encoder.encode(secret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scopes(values->values.addAll(scopes))
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).accessTokenTimeToLive(java.time.Duration.ofMinutes(5)).build()).build());
        jdbc.update("INSERT INTO integration_client(id,tenant_id,name) VALUES(?,UUID_TO_BIN(?),?)",id,tenant.toString(),name.strip());
        audit(actor,id,"CREATED");return new Created(view(id,tenant,name.strip(),true,0),secret);
    }
    /**
     * 修改状态或 scope 并推进版本，旧 Token 即时失效；失败不覆盖并发变更。
     * @param actor 权威平台操作者
     * @param id 不透明集成客户端标识
     * @param version 调用方读取的版本，非负整数
     * @param enabled 新启用状态，恢复也不恢复旧 Token
     * @param scopes 非空且仅包含已发布外部 scope
     * @return 新摘要，不返回密钥
     */
    @Transactional
    public View update(UUID actor,String id,int version,boolean enabled,Set<String> scopes){
        lock(actor);validate("valid",scopes);var policy=lockClient(id,version);
        var client=clients.findById(id);if(client==null)throw invalid();
        clients.save(RegisteredClient.from(client).scopes(values->{values.clear();values.addAll(scopes);}).build());
        jdbc.update("UPDATE integration_client SET enabled=?,authority_version=authority_version+1 WHERE id=?",enabled,id);
        jdbc.update("DELETE FROM oauth2_authorization WHERE registered_client_id=?",id);audit(actor,id,"AUTHORIZATION_UPDATED");
        events.publish(policy.tenant(),"INTEGRATION_AUTHORITY_CHANGED",id);
        String name=jdbc.queryForObject("SELECT name FROM integration_client WHERE id=?",String.class,id);
        return view(id,policy.tenant(),name,enabled,version+1);
    }
    /**
     * 原子轮换客户端密钥并撤销旧 Token；旧密钥在固定 600 秒过渡窗口内仍可认证。
     * @param actor 权威平台操作者
     * @param id 目标客户端标识
     * @param version 当前管理版本，冲突返回 409
     * @return 新密钥仅返回一次；过渡窗口内禁止再次轮换，避免第三把密钥静默替代
     */
    @Transactional
    public Created rotate(UUID actor,String id,int version){
        lock(actor);var policy=lockClient(id,version);var client=clients.findById(id);String secret=secret();
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT previous_secret_until>CURRENT_TIMESTAMP(6) FROM integration_client WHERE id=?",Boolean.class,id)))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"SECRET_ROTATION_ACTIVE","密钥过渡窗口尚未结束");
        jdbc.update("UPDATE integration_client SET previous_secret_hash=?,previous_secret_until=CURRENT_TIMESTAMP(6)+INTERVAL 600 SECOND WHERE id=?",client.getClientSecret(),id);
        clients.save(RegisteredClient.from(client).clientSecret(encoder.encode(secret)).build());
        jdbc.update("UPDATE integration_client SET authority_version=authority_version+1 WHERE id=?",id);
        jdbc.update("DELETE FROM oauth2_authorization WHERE registered_client_id=?",id);audit(actor,id,"SECRET_ROTATED");
        events.publish(policy.tenant(),"INTEGRATION_AUTHORITY_CHANGED",id);
        return new Created(view(id,policy.tenant(),jdbc.queryForObject("SELECT name FROM integration_client WHERE id=?",String.class,id),policy.enabled(),version+1),secret);
    }
    /** 权威查询同时验证租户状态；每次 Token 签发和访问均复核，故障不使用过期缓存放行。 */
    public Policy policy(String id){
        var values=jdbc.query("SELECT c.id,BIN_TO_UUID(c.tenant_id) tenant,c.authority_version,c.enabled AND t.enabled active FROM integration_client c JOIN platform_tenant t ON t.id=c.tenant_id WHERE c.id=?",
            (row,index)->new Policy(row.getString("id"),UUID.fromString(row.getString("tenant")),row.getInt("authority_version"),row.getBoolean("active")),id);
        return values.isEmpty()?null:values.getFirst();
    }
    private Policy lockClient(String id,int version){
        var rows=jdbc.queryForList("SELECT id FROM integration_client WHERE id=? AND authority_version=? FOR UPDATE",id,version);
        if(rows.isEmpty())throw AccountSettingsController.problem(HttpStatus.CONFLICT,"INTEGRATION_CHANGED","集成客户端已变化，请刷新");
        return policy(id);
    }
    private View view(String id,UUID tenant,String name,boolean enabled,int version){
        var client=clients.findById(id);return new View(id,tenant,name,enabled,version,client==null?Set.of():client.getScopes());
    }
    private void require(UUID actor){if(!platform.isAdministrator(actor))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
    private void lock(UUID actor){require(actor);if(jdbc.queryForList("SELECT p.account_id FROM platform_administrator p JOIN identity_account a ON a.id=p.account_id WHERE p.account_id=UUID_TO_BIN(?) AND p.enabled=TRUE AND a.enabled=TRUE FOR UPDATE",actor.toString()).isEmpty())throw new SecurityException("Platform authority required");}
    private void validate(String name,Set<String> scopes){if(name==null||name.isBlank()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl)||scopes==null||scopes.isEmpty()||!SUPPORTED.containsAll(scopes))throw invalid();}
    private String secret(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private void audit(UUID actor,String id,String action){jdbc.update("INSERT INTO integration_client_audit(actor_id,client_id,action) VALUES(UUID_TO_BIN(?),?,?)",actor.toString(),id,action);}
    private org.springframework.web.ErrorResponseException invalid(){return AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_INTEGRATION_INPUT","集成参数或租户状态不正确");}
}
