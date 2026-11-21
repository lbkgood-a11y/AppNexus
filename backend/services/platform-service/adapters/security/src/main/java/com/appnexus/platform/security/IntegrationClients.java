package com.appnexus.platform.security;
import com.appnexus.platform.persistence.identity.integration.IntegrationClientMapper;

import com.appnexus.platform.tenant.PlatformAdministration;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;

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
public class IntegrationClients implements DataAccessDeclaration.Executor {
    /** 固定平台管理资源；客户端密钥不进入可配置字段目录。 */
    public static final String RESOURCE = "integration.client";

    /** @return 由平台身份复核、固定租户绑定和专用响应投影约束的资源 */
    @Override public Set<String> enforcedResources(){return Set.of(RESOURCE);}

    private final IntegrationClientMapper statements;

    /** 当前正式可执行的外部 API scope；打印代理 scope 只允许受控代理路径，不授予其他租户 API。 */
    public static final Set<String> SUPPORTED=Set.of("dictionaries:read","dictionary-references:write","print-agent:operate","print:submit","business-object:resolve");
    
    private final RegisteredClientRepository clients;
    private final PasswordEncoder encoder;
    private final PlatformAdministration platform;
    private final ReliableEvents events;
    public IntegrationClients(RegisteredClientRepository clients,PasswordEncoder encoder,PlatformAdministration platform,ReliableEvents events,IntegrationClientMapper statements){this.statements=statements;
        this.clients=clients;this.encoder=encoder;this.platform=platform;this.events=events;
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
        return statements.summaries(tenant.toString()).stream().map(row->view((String)row.get("id"),tenant,(String)row.get("name"),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("authority_version")).intValue())).toList();
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
        var rows=statements.statement1(tenant.toString());
        if(rows.isEmpty())throw invalid();
        String id="partner-"+UUID.randomUUID(),secret=secret();
        clients.save(RegisteredClient.withId(id).clientId(id).clientSecret(encoder.encode(secret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scopes(values->values.addAll(scopes))
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).accessTokenTimeToLive(java.time.Duration.ofMinutes(5)).build()).build());
        statements.statement2(id,tenant.toString(),name.strip());
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
        statements.statement3(enabled,id);
        statements.statement4(id);audit(actor,id,"AUTHORIZATION_UPDATED");
        events.publish(policy.tenant(),"INTEGRATION_AUTHORITY_CHANGED",id);
        String name=statements.statement5(id);
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
        if(Boolean.TRUE.equals(statements.statement6(id)))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"SECRET_ROTATION_ACTIVE","密钥过渡窗口尚未结束");
        statements.statement7(client.getClientSecret(),id);
        clients.save(RegisteredClient.from(client).clientSecret(encoder.encode(secret)).build());
        statements.statement8(id);
        statements.statement9(id);audit(actor,id,"SECRET_ROTATED");
        events.publish(policy.tenant(),"INTEGRATION_AUTHORITY_CHANGED",id);
        return new Created(view(id,policy.tenant(),statements.statement10(id),policy.enabled(),version+1),secret);
    }
    /** 权威查询同时验证租户状态；每次 Token 签发和访问均复核，故障不使用过期缓存放行。 */
    public Policy policy(String id){
        var values=statements.policy(id).stream().map(row->new Policy((String)row.get("id"),UUID.fromString((String)row.get("tenant")),((Number)row.get("authority_version")).intValue(),Boolean.TRUE.equals(row.get("clientEnabled"))&&Boolean.TRUE.equals(row.get("tenantEnabled")))).toList();
        return values.isEmpty()?null:values.getFirst();
    }
    private Policy lockClient(String id,int version){
        var rows=statements.statement11(id,version);
        if(rows.isEmpty())throw AccountSettingsController.problem(HttpStatus.CONFLICT,"INTEGRATION_CHANGED","集成客户端已变化，请刷新");
        return policy(id);
    }
    private View view(String id,UUID tenant,String name,boolean enabled,int version){
        var client=clients.findById(id);return new View(id,tenant,name,enabled,version,client==null?Set.of():client.getScopes());
    }
    private void require(UUID actor){if(!platform.isAdministrator(actor))throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限");}
    private void lock(UUID actor){require(actor);if(statements.statement12(actor.toString()).isEmpty())throw new SecurityException("Platform authority required");}
    private void validate(String name,Set<String> scopes){if(name==null||name.isBlank()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl)||scopes==null||scopes.isEmpty()||!SUPPORTED.containsAll(scopes))throw invalid();}
    private String secret(){byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private void audit(UUID actor,String id,String action){statements.statement13(actor.toString(),id,action);}
    private org.springframework.web.ErrorResponseException invalid(){return AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_INTEGRATION_INPUT","集成参数或租户状态不正确");}
}
