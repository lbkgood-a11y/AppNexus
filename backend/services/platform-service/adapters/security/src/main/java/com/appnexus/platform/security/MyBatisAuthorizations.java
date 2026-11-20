package com.appnexus.platform.security;

import com.appnexus.platform.persistence.identity.oauth.OAuthAuthorizationRepositoryMapper;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** OAuth 标准授权存储；刷新重放和账号锁仍由 RotatingAuthorizationService 负责。 */
public final class MyBatisAuthorizations implements OAuth2AuthorizationService {
    private final OAuthAuthorizationRepositoryMapper mapper;
    private final OAuthAuthorizationCodec codec;

    /**
     * @param mapper 标准 OAuth 表固定 MyBatis 仓储，沿用外层事务
     * @param clients 已有注册客户端仓储，恢复授权时验证客户端存在
     */
    public MyBatisAuthorizations(OAuthAuthorizationRepositoryMapper mapper, RegisteredClientRepository clients) {
        this.mapper = mapper;
        this.codec = new OAuthAuthorizationCodec(clients);
    }

    /** @param authorization 框架生成的授权；不接受客户端自行构造的身份或版本 */
    public void save(OAuth2Authorization authorization) {
        mapper.saveAuthorization(OAuthRowProjection.values(codec.parameters(authorization)));
    }

    /** @param authorization 待撤销授权，删除参与调用方事务 */
    public void remove(OAuth2Authorization authorization) {
        mapper.removeAuthorization(authorization.getId());
    }

    /** @param id 标准授权标识 @return 持久化授权，未找到返回 null */
    public OAuth2Authorization findById(String id) {
        return codec.read(mapper.authorizationById(id));
    }

    /**
     * @param token 待匹配凭据，不记录日志
     * @param type 标准凭据类型，null 表示匹配全部受支持类型
     * @return 持久化授权，未找到返回 null；非法类型由固定仓储拒绝匹配
     */
    public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
        var found = mapper.byToken(token, type == null ? null : type.getValue());
        return found.isEmpty() ? null : codec.read(found.getFirst());
    }
}
