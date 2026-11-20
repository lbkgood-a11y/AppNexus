/*
 * Copyright 2004-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * 基于 Spring Security 7.1.2 JdbcOAuth2AuthorizationService 的标准字段映射改编。
 * 本地修改：以固定 MyBatis 行投影替代依赖 JDBC 静态列元数据的 LOB 访问。
 */
package com.appnexus.platform.security;
import java.sql.*;
import java.util.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.util.*;
/**
 * MyBatis 标准授权行编解码，沿用 Spring Security 7.1 列序及安全 JSON 白名单。
 * <p>框架 JDBC 映射器依赖服务构造时初始化的静态列元数据，不能独立调用。
 * 本类只转换固定 Mapper 的行与参数，不创建隐藏 JDBC 服务，不改变事务、Token 和刷新规则。
 */
@SuppressWarnings("removal")
final class OAuthAuthorizationCodec {
 private final RegisteredClientRepository registeredClientRepository;
 private final ObjectMapper objectMapper=new ObjectMapper();
 /** @param clients 已有注册客户端仓储，不创建客户端 */
 OAuthAuthorizationCodec(RegisteredClientRepository clients){
  this.registeredClientRepository=clients;
  objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
  objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
 }
 /** @param values 固定 Mapper 返回行，缺失表示未找到 @return 授权或 null */
 OAuth2Authorization read(Map<String,Object> values){return OAuthRowProjection.read(values,this::mapRow);}
 /** 固定行投影已将二进制列按 UTF-8 转换，不依赖 JDBC 服务静态元数据。 */
 private String getLobValue(ResultSet rs,String column)throws SQLException{return rs.getString(column);}
 /** 安全模块白名单恢复认证主体；损坏数据失败关闭。 */
 private Map<String,Object> parseMap(String data){
  if(!StringUtils.hasText(data))return Collections.emptyMap();
  try{return objectMapper.readValue(data,new TypeReference<Map<String,Object>>(){});}
  catch(Exception error){throw new IllegalArgumentException("OAuth 存储数据无法解析",error);}
 }
 /** 固定 Mapper 消费参数值，MySQL 驱动将 UTF-8 文本绑定到标准二进制列。 */
 private static SqlParameterValue mapToSqlParameter(String column,String value){return new SqlParameterValue(Types.VARCHAR,value);}
 private String writeValueAsString(Map<String,Object> data)throws Exception{return objectMapper.writeValueAsString(data);}
private static final String AUTHORIZATION_CODE_VALUE="authorization_code_value";
private static final String AUTHORIZATION_CODE_METADATA="authorization_code_metadata";
private static final String ACCESS_TOKEN_VALUE="access_token_value";
private static final String ACCESS_TOKEN_METADATA="access_token_metadata";
private static final String OIDC_ID_TOKEN_VALUE="oidc_id_token_value";
private static final String OIDC_ID_TOKEN_METADATA="oidc_id_token_metadata";
private static final String REFRESH_TOKEN_VALUE="refresh_token_value";
private static final String REFRESH_TOKEN_METADATA="refresh_token_metadata";
private static final String USER_CODE_VALUE="user_code_value";
private static final String USER_CODE_METADATA="user_code_metadata";
private static final String DEVICE_CODE_VALUE="device_code_value";
private static final String DEVICE_CODE_METADATA="device_code_metadata";
		@SuppressWarnings("unchecked")
		private OAuth2Authorization mapRow(ResultSet rs, int rowNum) throws SQLException {
			String registeredClientId = rs.getString("registered_client_id");
			RegisteredClient registeredClient = this.registeredClientRepository.findById(registeredClientId);
			if (registeredClient == null) {
				throw new DataRetrievalFailureException("The RegisteredClient with id '" + registeredClientId
						+ "' was not found in the RegisteredClientRepository.");
			}

			OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient);
			String id = rs.getString("id");
			String principalName = rs.getString("principal_name");
			String authorizationGrantType = rs.getString("authorization_grant_type");
			Set<String> authorizedScopes = Collections.emptySet();
			String authorizedScopesString = rs.getString("authorized_scopes");
			if (authorizedScopesString != null) {
				authorizedScopes = StringUtils.commaDelimitedListToSet(authorizedScopesString);
			}
			Map<String, Object> attributes = parseMap(getLobValue(rs, "attributes"));

			builder.id(id)
				.principalName(principalName)
				.authorizationGrantType(new AuthorizationGrantType(authorizationGrantType))
				.authorizedScopes(authorizedScopes)
				.attributes((attrs) -> attrs.putAll(attributes));

			String state = rs.getString("state");
			if (StringUtils.hasText(state)) {
				builder.attribute(OAuth2ParameterNames.STATE, state);
			}

			Instant tokenIssuedAt;
			Instant tokenExpiresAt;
			String authorizationCodeValue = getLobValue(rs, AUTHORIZATION_CODE_VALUE);

			if (StringUtils.hasText(authorizationCodeValue)) {
				tokenIssuedAt = rs.getTimestamp("authorization_code_issued_at").toInstant();
				tokenExpiresAt = rs.getTimestamp("authorization_code_expires_at").toInstant();
				Map<String, Object> authorizationCodeMetadata = parseMap(getLobValue(rs, AUTHORIZATION_CODE_METADATA));

				OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(authorizationCodeValue,
						tokenIssuedAt, tokenExpiresAt);
				builder.token(authorizationCode, (metadata) -> metadata.putAll(authorizationCodeMetadata));
			}

			String accessTokenValue = getLobValue(rs, ACCESS_TOKEN_VALUE);
			if (StringUtils.hasText(accessTokenValue)) {
				tokenIssuedAt = rs.getTimestamp("access_token_issued_at").toInstant();
				tokenExpiresAt = rs.getTimestamp("access_token_expires_at").toInstant();
				Map<String, Object> accessTokenMetadata = parseMap(getLobValue(rs, ACCESS_TOKEN_METADATA));
				OAuth2AccessToken.TokenType tokenType = null;
				if (OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(rs.getString("access_token_type"))) {
					tokenType = OAuth2AccessToken.TokenType.BEARER;
				}
				else if (OAuth2AccessToken.TokenType.DPOP.getValue()
					.equalsIgnoreCase(rs.getString("access_token_type"))) {
					tokenType = OAuth2AccessToken.TokenType.DPOP;
				}
				Assert.notNull(tokenType, "access_token_type must be BEARER or DPOP");

				Set<String> scopes = Collections.emptySet();
				String accessTokenScopes = rs.getString("access_token_scopes");
				if (accessTokenScopes != null) {
					scopes = StringUtils.commaDelimitedListToSet(accessTokenScopes);
				}
				OAuth2AccessToken accessToken = new OAuth2AccessToken(tokenType, accessTokenValue, tokenIssuedAt,
						tokenExpiresAt, scopes);
				builder.token(accessToken, (metadata) -> metadata.putAll(accessTokenMetadata));
			}

			String oidcIdTokenValue = getLobValue(rs, OIDC_ID_TOKEN_VALUE);
			if (StringUtils.hasText(oidcIdTokenValue)) {
				tokenIssuedAt = rs.getTimestamp("oidc_id_token_issued_at").toInstant();
				tokenExpiresAt = rs.getTimestamp("oidc_id_token_expires_at").toInstant();
				Map<String, Object> oidcTokenMetadata = parseMap(getLobValue(rs, OIDC_ID_TOKEN_METADATA));

				@SuppressWarnings("unchecked")
				Map<String, Object> idTokenClaims = (Map<String, Object>) oidcTokenMetadata
					.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
				if (idTokenClaims == null) {
					idTokenClaims = Collections.emptyMap();
				}
				OidcIdToken oidcToken = new OidcIdToken(oidcIdTokenValue, tokenIssuedAt, tokenExpiresAt, idTokenClaims);
				builder.token(oidcToken, (metadata) -> metadata.putAll(oidcTokenMetadata));
			}

			String refreshTokenValue = getLobValue(rs, REFRESH_TOKEN_VALUE);
			if (StringUtils.hasText(refreshTokenValue)) {
				tokenIssuedAt = rs.getTimestamp("refresh_token_issued_at").toInstant();
				tokenExpiresAt = null;
				Timestamp refreshTokenExpiresAt = rs.getTimestamp("refresh_token_expires_at");
				if (refreshTokenExpiresAt != null) {
					tokenExpiresAt = refreshTokenExpiresAt.toInstant();
				}
				Map<String, Object> refreshTokenMetadata = parseMap(getLobValue(rs, REFRESH_TOKEN_METADATA));

				OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(refreshTokenValue, tokenIssuedAt,
						tokenExpiresAt);
				builder.token(refreshToken, (metadata) -> metadata.putAll(refreshTokenMetadata));
			}

			String userCodeValue = getLobValue(rs, USER_CODE_VALUE);
			if (StringUtils.hasText(userCodeValue)) {
				tokenIssuedAt = rs.getTimestamp("user_code_issued_at").toInstant();
				tokenExpiresAt = rs.getTimestamp("user_code_expires_at").toInstant();
				Map<String, Object> userCodeMetadata = parseMap(getLobValue(rs, USER_CODE_METADATA));

				OAuth2UserCode userCode = new OAuth2UserCode(userCodeValue, tokenIssuedAt, tokenExpiresAt);
				builder.token(userCode, (metadata) -> metadata.putAll(userCodeMetadata));
			}

			String deviceCodeValue = getLobValue(rs, DEVICE_CODE_VALUE);
			if (StringUtils.hasText(deviceCodeValue)) {
				tokenIssuedAt = rs.getTimestamp("device_code_issued_at").toInstant();
				tokenExpiresAt = rs.getTimestamp("device_code_expires_at").toInstant();
				Map<String, Object> deviceCodeMetadata = parseMap(getLobValue(rs, DEVICE_CODE_METADATA));

				OAuth2DeviceCode deviceCode = new OAuth2DeviceCode(deviceCodeValue, tokenIssuedAt, tokenExpiresAt);
				builder.token(deviceCode, (metadata) -> metadata.putAll(deviceCodeMetadata));
			}

			return builder.build();
		}

/** @param authorization 框架授权及凭据元数据 @return 固定 Mapper 的 33 列参数 */
		List<SqlParameterValue> parameters(OAuth2Authorization authorization) {
			List<SqlParameterValue> parameters = new ArrayList<>();
			parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getId()));
			parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getRegisteredClientId()));
			parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getPrincipalName()));
			parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getAuthorizationGrantType().getValue()));

			String authorizedScopes = null;
			if (!CollectionUtils.isEmpty(authorization.getAuthorizedScopes())) {
				authorizedScopes = StringUtils.collectionToDelimitedString(authorization.getAuthorizedScopes(), ",");
			}
			parameters.add(new SqlParameterValue(Types.VARCHAR, authorizedScopes));

			String attributes = writeMap(authorization.getAttributes());
			parameters.add(mapToSqlParameter("attributes", attributes));

			String state = null;
			String authorizationState = authorization.getAttribute(OAuth2ParameterNames.STATE);
			if (StringUtils.hasText(authorizationState)) {
				state = authorizationState;
			}
			parameters.add(new SqlParameterValue(Types.VARCHAR, state));

			OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization
				.getToken(OAuth2AuthorizationCode.class);
			List<SqlParameterValue> authorizationCodeSqlParameters = toSqlParameterList(AUTHORIZATION_CODE_VALUE,
					AUTHORIZATION_CODE_METADATA, authorizationCode);
			parameters.addAll(authorizationCodeSqlParameters);

			OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
			List<SqlParameterValue> accessTokenSqlParameters = toSqlParameterList(ACCESS_TOKEN_VALUE,
					ACCESS_TOKEN_METADATA, accessToken);
			parameters.addAll(accessTokenSqlParameters);
			String accessTokenType = null;
			String accessTokenScopes = null;
			if (accessToken != null) {
				accessTokenType = accessToken.getToken().getTokenType().getValue();
				if (!CollectionUtils.isEmpty(accessToken.getToken().getScopes())) {
					accessTokenScopes = StringUtils.collectionToDelimitedString(accessToken.getToken().getScopes(),
							",");
				}
			}
			parameters.add(new SqlParameterValue(Types.VARCHAR, accessTokenType));
			parameters.add(new SqlParameterValue(Types.VARCHAR, accessTokenScopes));

			OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
			List<SqlParameterValue> oidcIdTokenSqlParameters = toSqlParameterList(OIDC_ID_TOKEN_VALUE,
					OIDC_ID_TOKEN_METADATA, oidcIdToken);
			parameters.addAll(oidcIdTokenSqlParameters);

			OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
			List<SqlParameterValue> refreshTokenSqlParameters = toSqlParameterList(REFRESH_TOKEN_VALUE,
					REFRESH_TOKEN_METADATA, refreshToken);
			parameters.addAll(refreshTokenSqlParameters);

			OAuth2Authorization.Token<OAuth2UserCode> userCode = authorization.getToken(OAuth2UserCode.class);
			List<SqlParameterValue> userCodeSqlParameters = toSqlParameterList(USER_CODE_VALUE, USER_CODE_METADATA,
					userCode);
			parameters.addAll(userCodeSqlParameters);

			OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = authorization.getToken(OAuth2DeviceCode.class);
			List<SqlParameterValue> deviceCodeSqlParameters = toSqlParameterList(DEVICE_CODE_VALUE,
					DEVICE_CODE_METADATA, deviceCode);
			parameters.addAll(deviceCodeSqlParameters);

			return parameters;
		}

		private <T extends OAuth2Token> List<SqlParameterValue> toSqlParameterList(String tokenColumnName,
				String tokenMetadataColumnName, OAuth2Authorization.Token<T> token) {

			List<SqlParameterValue> parameters = new ArrayList<>();
			String tokenValue = null;
			Timestamp tokenIssuedAt = null;
			Timestamp tokenExpiresAt = null;
			String metadata = null;
			if (token != null) {
				tokenValue = token.getToken().getTokenValue();
				if (token.getToken().getIssuedAt() != null) {
					tokenIssuedAt = Timestamp.from(token.getToken().getIssuedAt());
				}
				if (token.getToken().getExpiresAt() != null) {
					tokenExpiresAt = Timestamp.from(token.getToken().getExpiresAt());
				}
				metadata = writeMap(token.getMetadata());
			}

			parameters.add(mapToSqlParameter(tokenColumnName, tokenValue));
			parameters.add(new SqlParameterValue(Types.TIMESTAMP, tokenIssuedAt));
			parameters.add(new SqlParameterValue(Types.TIMESTAMP, tokenExpiresAt));
			parameters.add(mapToSqlParameter(tokenMetadataColumnName, metadata));
			return parameters;
		}

		private String writeMap(Map<String, Object> data) {
			try {
				return writeValueAsString(data);
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(ex.getMessage(), ex);
			}
		}

}
