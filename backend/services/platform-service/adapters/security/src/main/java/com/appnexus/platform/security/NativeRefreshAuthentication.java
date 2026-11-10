package com.appnexus.platform.security;

import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** 公开原生客户端刷新不携带伪造客户端密钥；仅在标准刷新授权中接受显式注册的 NONE 客户端。 */
public final class NativeRefreshAuthentication implements AuthenticationProvider {
    private final RegisteredClientRepository clients;
    /** @param clients 权威客户端注册仓储；公开客户端必须显式启用刷新且要求 PKCE */
    public NativeRefreshAuthentication(RegisteredClientRepository clients){this.clients=clients;}
    /** 单值参数及固定授权类型门禁；授权码首次换取仍由框架强制 PKCE S256。 */
    public static AuthenticationConverter converter(){return request->{
        if(!"refresh_token".equals(request.getParameter("grant_type"))||request.getHeader("Authorization")!=null)return null;
        String[] ids=request.getParameterValues("client_id");
        if(ids==null||ids.length!=1||ids[0].length()>100||request.getParameter("client_secret")!=null)return null;
        return new OAuth2ClientAuthenticationToken(ids[0],ClientAuthenticationMethod.NONE,null,Map.of("native_refresh",true));
    };}
    public boolean supports(Class<?> type){return OAuth2ClientAuthenticationToken.class.isAssignableFrom(type);}
    public Authentication authenticate(Authentication authentication){
        var input=(OAuth2ClientAuthenticationToken)authentication;
        if(!ClientAuthenticationMethod.NONE.equals(input.getClientAuthenticationMethod())||!Boolean.TRUE.equals(input.getAdditionalParameters().get("native_refresh")))return null;
        var client=clients.findByClientId(input.getPrincipal().toString());
        if(client==null||!client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)||
                !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)||!client.getClientSettings().isRequireProofKey())
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        return new OAuth2ClientAuthenticationToken(client,ClientAuthenticationMethod.NONE,null);
    }
}
