package com.appnexus.platform.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 仅 OAuth 客户端密钥认证使用；账号密码仍使用原 Argon2id 编码器，不参与集成双密钥策略。 */
public final class RotatingClientPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate;
    private final JdbcTemplate jdbc;
    public RotatingClientPasswordEncoder(PasswordEncoder delegate,JdbcTemplate jdbc){this.delegate=delegate;this.jdbc=jdbc;}
    public String encode(CharSequence raw){return delegate.encode(raw);}
    /**
     * 先验证当前哈希，失败才按相同注册哈希定位有效旧密钥，不能跨客户端匹配。
     * @param raw 框架从 HTTP Basic 提取的客户端密钥，不记录日志
     * @param encoded 当前注册密钥的 Argon2id 哈希
     * @return 当前密钥匹配或前一密钥在 600 秒过渡窗口内匹配时为 true
     */
    public boolean matches(CharSequence raw,String encoded){
        if(delegate.matches(raw,encoded))return true;
        var previous=jdbc.queryForList("SELECT c.previous_secret_hash FROM integration_client c JOIN oauth2_registered_client r ON r.id=c.id JOIN platform_tenant t ON t.id=c.tenant_id WHERE r.client_secret=? AND c.enabled=TRUE AND t.enabled=TRUE AND c.previous_secret_until>CURRENT_TIMESTAMP(6) AND c.previous_secret_hash IS NOT NULL",String.class,encoded);
        return previous.size()==1&&delegate.matches(raw,previous.getFirst());
    }
}
