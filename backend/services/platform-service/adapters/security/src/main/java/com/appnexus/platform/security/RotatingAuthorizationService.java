package com.appnexus.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;

/**
 * 标准 JDBC OAuth 仓储的轮换边界；SQL 仅访问全局认证表，不用于租户业务查询。
 * 刷新在权威授权行锁中执行，旧摘要保留到族到期；重放删除对应设备授权，不撤销其他设备。
 */
public final class RotatingAuthorizationService implements OAuth2AuthorizationService {
    /** 授权记录中的可信租户、成员和版本；由受控绑定用例写入，不能来自任意请求头。 */
    public static final String TENANT="appnexus.nativeTenant",MEMBER="appnexus.nativeMember",VERSION="appnexus.nativeVersion";
    private final OAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final com.appnexus.platform.audit.SecurityLogRepository logs;
    /**
     * @param delegate 框架 JDBC OAuth 仓储，不替换标准协议及序列化边界
     * @param jdbc 仅访问全局账号、授权及刷新摘要表的内部数据库入口
     * @param manager 与账号、OAuth 授权和安全审计共用的事务管理器
     * @param logs 重放撤销审计端口；记录失败时拒绝刷新，不返回新凭证
     */
    public RotatingAuthorizationService(OAuth2AuthorizationService delegate,JdbcTemplate jdbc,PlatformTransactionManager manager,
            com.appnexus.platform.audit.SecurityLogRepository logs){
        this.delegate=delegate;this.jdbc=jdbc;this.transactions=new TransactionTemplate(manager);this.logs=logs;
        // 等待账号/授权锁后必须读取最新提交状态，不能使用首次查询创建的旧快照。
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    /** 框架授权及当前刷新摘要同事务落库，失败不能返回未受重放保护的 Refresh Token。 */
    public void save(OAuth2Authorization authorization){
        transactions.executeWithoutResult(status->{
            delegate.save(authorization);
            var refresh=authorization.getRefreshToken();
            if(refresh!=null){
                var token=refresh.getToken();
                jdbc.update("INSERT IGNORE INTO identity_refresh_history(token_hash,authorization_id,registered_client_id,expires_at) VALUES(?,?,?,?)",
                    hash(token.getTokenValue()),authorization.getId(),authorization.getRegisteredClientId(),Timestamp.from(token.getExpiresAt()));
            }
        });
    }
    /** 显式撤销只删除授权；保留历史摘要，旧 Token 不能被重新解释为新设备授权。 */
    public void remove(OAuth2Authorization authorization){delegate.remove(authorization);}
    public OAuth2Authorization findById(String id){return delegate.findById(id);}
    /**
     * 标准凭证检索，不允许历史刷新摘要被当成有效当前 Token。
     * @param token 框架内部凭证原文，禁止记录日志
     * @param type 标准 Token 类型；null 表示由框架按支持类型检索
     * @return 当前授权，不存在时返回 null
     */
    public OAuth2Authorization findByToken(String token,OAuth2TokenType type){return delegate.findByToken(token,type);}
    /**
     * 以账号、授权固定锁顺序绑定租户；变更后旧访问凭证失效，刷新仍走标准 OAuth。
     * @param token 当前有效 Access Token，仅用于服务端定位设备授权
     * @param account 经过 introspection 的不可变账号 UUID
     * @param tenant 客户端选择意图，必须复核当前有效成员
     * @param expectedVersion 客户端已知版本，首次零；陈旧请求返回 409
     * @param tenants 权威租户成员查询端口
     * @return 绑定上下文及必须刷新的标记
     */
    public NativeTenantContextController.Context bindTenant(String token,java.util.UUID account,java.util.UUID tenant,long expectedVersion,
            com.appnexus.platform.tenant.TenantAccess tenants){
        return transactions.execute(status->{
            if(jdbc.queryForList("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(?) AND enabled=TRUE FOR UPDATE",account.toString()).isEmpty())throw invalid();
            var existing=delegate.findByToken(token,OAuth2TokenType.ACCESS_TOKEN);
            if(existing==null||!account.toString().equals(existing.getPrincipalName()))throw invalid();
            jdbc.queryForList("SELECT id FROM oauth2_authorization WHERE id=? FOR UPDATE",existing.getId());
            existing=delegate.findById(existing.getId());
            if(existing==null||existing.getAccessToken()==null||!existing.getAccessToken().isActive()||!existing.getAccessToken().getToken().getTokenValue().equals(token))throw invalid();
            long version=existing.getAttribute(VERSION) instanceof Number number?number.longValue():0;
            if(version!=expectedVersion)throw AccountSettingsController.problem(org.springframework.http.HttpStatus.CONFLICT,"TENANT_CONTEXT_CHANGED","租户上下文已变化");
            var member=tenants.available(account).stream().filter(item->item.tenantId().equals(tenant)).findFirst()
                .orElseThrow(()->AccountSettingsController.problem(org.springframework.http.HttpStatus.FORBIDDEN,"TENANT_UNAVAILABLE","租户不可访问"));
            var updated=OAuth2Authorization.from(existing).attribute(TENANT,tenant.toString()).attribute(MEMBER,member.memberId().toString())
                .attribute(VERSION,Math.toIntExact(version+1))
                .token(existing.getAccessToken().getToken(),metadata->metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME,true)).build();
            save(updated);return new NativeTenantContextController.Context(tenant,member.memberId(),version+1,true);
        });
    }

    /**
     * 装饰标准刷新 Provider，保留 scope、授权类型、客户端及 Token 标准校验。
     * @param provider 框架默认刷新 Provider，不能替换为自定义 Token 协议
     * @return 在数据库事务与行锁下运行的 Provider
     */
    public AuthenticationProvider protect(AuthenticationProvider provider){
        return new AuthenticationProvider(){
            public boolean supports(Class<?> type){return provider.supports(type);}
            public Authentication authenticate(Authentication authentication){
                var request=(OAuth2RefreshTokenAuthenticationToken)authentication;
                var client=(OAuth2ClientAuthenticationToken)request.getPrincipal();
                if(client.getRegisteredClient()==null)throw invalid();
                // 重放撤销必须提交后才抛 OAuth 错误，否则运行时异常会回滚撤销操作。
                Authentication result=transactions.execute(status->{
                    // 历史行使用锁定读，避免 MySQL REPEATABLE READ 的早期快照在等待后仍读到旧授权。
                    var history=jdbc.queryForList("SELECT authorization_id,registered_client_id,expires_at FROM identity_refresh_history WHERE token_hash=? FOR UPDATE",hash(request.getRefreshToken()));
                    if(history.size()!=1)return null;
                    var entry=history.getFirst();
                    if(!Objects.equals(entry.get("registered_client_id"),client.getRegisteredClient().getId())||
                            !((Timestamp)entry.get("expires_at")).toInstant().isAfter(java.time.Instant.now()))return null;
                    String id=(String)entry.get("authorization_id");
                    var owners=jdbc.queryForList("SELECT principal_name FROM oauth2_authorization WHERE id=?",id);
                    if(owners.isEmpty())return null;
                    String owner=(String)owners.getFirst().get("principal_name");
                    // 与改密、MFA 和管理员撤销统一先锁账号再锁授权；停用账号不能继续刷新。
                    var active=jdbc.queryForList("SELECT id FROM identity_account WHERE id=UUID_TO_BIN(?) AND enabled=TRUE FOR UPDATE",owner);
                    if(active.isEmpty())return null;
                    var rows=jdbc.queryForList("SELECT id FROM oauth2_authorization WHERE id=? FOR UPDATE",id);
                    if(rows.isEmpty())return null;
                    var current=delegate.findById(id);
                    if(current==null)return null;
                    var token=current.getRefreshToken();
                    if(token==null||!MessageDigest.isEqual(hash(token.getToken().getTokenValue()),hash(request.getRefreshToken()))){
                        delegate.remove(current);
                        logs.append(java.util.UUID.fromString(current.getPrincipalName()),com.appnexus.platform.audit.SecurityLog.Action.REFRESH_TOKEN_REPLAY);
                        return null;
                    }
                    return provider.authenticate(authentication);
                });
                if(result==null)throw invalid();return result;
            }
        };
    }
    private static OAuth2AuthenticationException invalid(){return new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);}
    private static byte[] hash(String value){
        try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
