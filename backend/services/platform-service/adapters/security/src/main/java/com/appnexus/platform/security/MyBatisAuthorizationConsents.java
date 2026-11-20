package com.appnexus.platform.security;
import com.appnexus.platform.persistence.identity.oauth.OAuthAuthorizationRepositoryMapper;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
/** 标准授权同意固定MyBatis存储，保留框架权限文本和注册客户端引用协议。 */
public final class MyBatisAuthorizationConsents implements OAuth2AuthorizationConsentService {
 private final OAuthAuthorizationRepositoryMapper mapper;
 private final JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentRowMapper rows;
 private final JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentParametersMapper parameters=new JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentParametersMapper();
 public MyBatisAuthorizationConsents(OAuthAuthorizationRepositoryMapper mapper,RegisteredClientRepository clients){this.mapper=mapper;this.rows=new JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentRowMapper(clients);}
 public void save(OAuth2AuthorizationConsent consent){mapper.saveConsent(OAuthRowProjection.values(parameters.apply(consent)));}
 public void remove(OAuth2AuthorizationConsent consent){mapper.removeConsent(consent.getRegisteredClientId(),consent.getPrincipalName());}
 public OAuth2AuthorizationConsent findById(String client,String principal){return OAuthRowProjection.read(mapper.consent(client,principal),rows);}
}
