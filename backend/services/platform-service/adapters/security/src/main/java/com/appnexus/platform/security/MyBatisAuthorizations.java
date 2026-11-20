package com.appnexus.platform.security;
import com.appnexus.platform.persistence.identity.oauth.OAuthAuthorizationRepositoryMapper;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
/** OAuth标准授权存储；刷新重放和账号锁仍由RotatingAuthorizationService负责，不引入并行Token体系。 */
public final class MyBatisAuthorizations implements OAuth2AuthorizationService {
 private final OAuthAuthorizationRepositoryMapper mapper;
 private final JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rows;
 private final JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parameters=new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
 public MyBatisAuthorizations(OAuthAuthorizationRepositoryMapper mapper,RegisteredClientRepository clients){this.mapper=mapper;this.rows=new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(clients);}
 public void save(OAuth2Authorization authorization){mapper.saveAuthorization(OAuthRowProjection.values(parameters.apply(authorization)));}
 public void remove(OAuth2Authorization authorization){mapper.removeAuthorization(authorization.getId());}
 public OAuth2Authorization findById(String id){return OAuthRowProjection.read(mapper.authorizationById(id),rows);}
 public OAuth2Authorization findByToken(String token,OAuth2TokenType type){var found=mapper.byToken(token,type==null?null:type.getValue());return found.isEmpty()?null:OAuthRowProjection.read(found.getFirst(),rows);}
}
