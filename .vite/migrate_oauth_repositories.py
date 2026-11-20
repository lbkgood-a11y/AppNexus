from pathlib import Path
import re
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security');schema=Path('backend/services/platform-service/adapters/persistence/src/main/resources/db/migration/V2__oauth_authorizations.sql').read_text(encoding='utf-8');tables={}
for table,body in re.findall(r'CREATE TABLE (\w+) \(([\s\S]*?)\n\);',schema):tables[table]=re.findall(r'^\s+(\w+) (?:varchar|timestamp|MEDIUMBLOB)',body,re.M)
methods=[]
for key,table in [('client','oauth2_registered_client'),('authorization','oauth2_authorization'),('consent','oauth2_authorization_consent')]:
 cols=tables[table];values=','.join('#{values['+str(i)+']}' for i in range(len(cols)));update=','.join(c+'=VALUES('+c+')' for c in cols if c not in ['id','registered_client_id','principal_name'])
 methods.append(' /** 框架标准参数映射按固定列序写入，不改序列化或Token格式。 */\n @Insert("INSERT INTO '+table+'('+','.join(cols)+') VALUES('+values+') ON DUPLICATE KEY UPDATE '+update+'") int save'+key.title()+'(@Param("values") List<Object> values);')
methods+=[''' @Select("SELECT * FROM oauth2_registered_client WHERE id=#{id}") Map<String,Object> clientById(@Param("id") String id);
 @Select("SELECT * FROM oauth2_registered_client WHERE client_id=#{id}") Map<String,Object> clientByClientId(@Param("id") String id);
 @Select("SELECT * FROM oauth2_authorization WHERE id=#{id}") Map<String,Object> authorizationById(@Param("id") String id);
 @Delete("DELETE FROM oauth2_authorization WHERE id=#{id}") int removeAuthorization(@Param("id") String id);
 @Select("SELECT * FROM oauth2_authorization_consent WHERE registered_client_id=#{client} AND principal_name=#{principal}") Map<String,Object> consent(@Param("client") String client,@Param("principal") String principal);
 @Delete("DELETE FROM oauth2_authorization_consent WHERE registered_client_id=#{client} AND principal_name=#{principal}") int removeConsent(@Param("client") String client,@Param("principal") String principal);
 @SelectProvider(type=TokenSql.class,method="query") List<Map<String,Object>> byToken(@Param("value") String value,@Param("kind") String kind);
 final class TokenSql {
  private TokenSql(){}
  /** @param parameters 框架Token类型及凭据，不允许任意列名 @return 固定Token列查询，凭据始终绑定且不记录日志 */
  public static String query(Map<String,Object> parameters){var columns=Map.of("state","state","code","authorization_code_value","access_token","access_token_value","id_token","oidc_id_token_value","refresh_token","refresh_token_value","user_code","user_code_value","device_code","device_code_value");String kind=(String)parameters.get("kind");if(kind!=null&&!columns.containsKey(kind))return "SELECT * FROM oauth2_authorization WHERE 1=0";var matches=new java.util.ArrayList<String>();for(var entry:columns.entrySet())if(kind==null||kind.equals(entry.getKey()))matches.add(entry.getValue()+"=#{value}");return "SELECT * FROM oauth2_authorization WHERE "+String.join(" OR ",matches);}
 }''']
(root/'OAuthRepositoryMapper.java').write_text('''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** OAuth标准表固定MyBatis存储，保留上游字段及序列化，不加入业务审计列。 */
@Mapper
@PlatformScoped(tables={"oauth2_registered_client","oauth2_authorization","oauth2_authorization_consent"})
public interface OAuthRepositoryMapper {
'''+ '\n'.join(methods)+'\n}\n',encoding='utf-8')
(root/'OAuthRowProjection.java').write_text('''package com.appnexus.platform.security;
import java.util.*;
import java.sql.*;
import org.springframework.jdbc.core.RowMapper;
/** 已查询行交给框架原RowMapper反序列化；代理不访问数据库，只投影固定Mapper结果。 */
final class OAuthRowProjection {
 private OAuthRowProjection(){}
 /** @param values 原固定查询行 @param mapper 框架公开RowMapper @return 原框架协议对象，未知方法或损坏数据失败关闭 */
 static <T>T read(Map<String,Object> values,RowMapper<T> mapper){if(values==null||values.isEmpty())return null;var keys=new ArrayList<>(values.keySet());try{ResultSet rs=(ResultSet)java.lang.reflect.Proxy.newProxyInstance(OAuthRowProjection.class.getClassLoader(),new Class<?>[]{ResultSet.class},(proxy,method,args)->{String name=method.getName();if(name.equals("wasNull"))return false;if(name.equals("getMetaData"))throw new UnsupportedOperationException("Metadata unavailable for fixed OAuth row");if(name.equals("close"))return null;if(name.equals("isClosed"))return false;if(args==null||args.length==0)throw new UnsupportedOperationException(name);String key=args[0] instanceof Number index?keys.get(index.intValue()-1):args[0].toString();Object value=values.get(key);return switch(name){case "getString"->value==null?null:value instanceof byte[] bytes?new String(bytes,java.nio.charset.StandardCharsets.UTF_8):value.toString();case "getBytes"->value==null?null:value instanceof byte[] bytes?bytes:value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);case "getTimestamp"->value==null?null:value instanceof Timestamp time?time:value instanceof java.time.LocalDateTime time?Timestamp.valueOf(time):Timestamp.from((java.time.Instant)value);case "getObject"->value;case "getBlob"->value==null?null:new javax.sql.rowset.serial.SerialBlob((byte[])value);case "getClob"->value==null?null:new javax.sql.rowset.serial.SerialClob(value.toString().toCharArray());case "getBoolean"->Boolean.TRUE.equals(value)||value instanceof Number number&&number.intValue()!=0;case "getInt"->value==null?0:((Number)value).intValue();case "getLong"->value==null?0L:((Number)value).longValue();default->throw new UnsupportedOperationException("Unsupported fixed OAuth projection: "+name);};});return mapper.mapRow(rs,0);}catch(SQLException error){throw new IllegalStateException("OAuth stored representation invalid",error);}}
 static List<Object> values(List<org.springframework.jdbc.core.SqlParameterValue> parameters){return parameters.stream().map(org.springframework.jdbc.core.SqlParameterValue::getValue).toList();}
}
''',encoding='utf-8')
(root/'MyBatisRegisteredClients.java').write_text('''package com.appnexus.platform.security;
import org.springframework.security.oauth2.server.authorization.client.*;
/** MyBatis注册客户端仓储；JSON和参数映射复用框架公开映射器，密钥保持原哈希协议。 */
public final class MyBatisRegisteredClients implements RegisteredClientRepository {
 private final OAuthRepositoryMapper mapper;
 private final JdbcRegisteredClientRepository.RegisteredClientRowMapper rows=new JdbcRegisteredClientRepository.RegisteredClientRowMapper();
 private final JdbcRegisteredClientRepository.RegisteredClientParametersMapper parameters=new JdbcRegisteredClientRepository.RegisteredClientParametersMapper();
 public MyBatisRegisteredClients(OAuthRepositoryMapper mapper){this.mapper=mapper;}
 public void save(RegisteredClient client){mapper.saveClient(OAuthRowProjection.values(parameters.apply(client)));}
 public RegisteredClient findById(String id){return OAuthRowProjection.read(mapper.clientById(id),rows);}
 public RegisteredClient findByClientId(String id){return OAuthRowProjection.read(mapper.clientByClientId(id),rows);}
}
''',encoding='utf-8')
(root/'MyBatisAuthorizations.java').write_text('''package com.appnexus.platform.security;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
/** OAuth标准授权存储；刷新重放和账号锁仍由RotatingAuthorizationService负责，不引入并行Token体系。 */
public final class MyBatisAuthorizations implements OAuth2AuthorizationService {
 private final OAuthRepositoryMapper mapper;
 private final JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rows;
 private final JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parameters=new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
 public MyBatisAuthorizations(OAuthRepositoryMapper mapper,RegisteredClientRepository clients){this.mapper=mapper;this.rows=new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(clients);}
 public void save(OAuth2Authorization authorization){mapper.saveAuthorization(OAuthRowProjection.values(parameters.apply(authorization)));}
 public void remove(OAuth2Authorization authorization){mapper.removeAuthorization(authorization.getId());}
 public OAuth2Authorization findById(String id){return OAuthRowProjection.read(mapper.authorizationById(id),rows);}
 public OAuth2Authorization findByToken(String token,OAuth2TokenType type){var found=mapper.byToken(token,type==null?null:type.getValue());return found.isEmpty()?null:OAuthRowProjection.read(found.getFirst(),rows);}
}
''',encoding='utf-8')
(root/'MyBatisAuthorizationConsents.java').write_text('''package com.appnexus.platform.security;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
/** 标准授权同意固定MyBatis存储，保留框架权限文本和注册客户端引用协议。 */
public final class MyBatisAuthorizationConsents implements OAuth2AuthorizationConsentService {
 private final OAuthRepositoryMapper mapper;
 private final JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentRowMapper rows;
 private final JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentParametersMapper parameters=new JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentParametersMapper();
 public MyBatisAuthorizationConsents(OAuthRepositoryMapper mapper,RegisteredClientRepository clients){this.mapper=mapper;this.rows=new JdbcOAuth2AuthorizationConsentService.OAuth2AuthorizationConsentRowMapper(clients);}
 public void save(OAuth2AuthorizationConsent consent){mapper.saveConsent(OAuthRowProjection.values(parameters.apply(consent)));}
 public void remove(OAuth2AuthorizationConsent consent){mapper.removeConsent(consent.getRegisteredClientId(),consent.getPrincipalName());}
 public OAuth2AuthorizationConsent findById(String client,String principal){return OAuthRowProjection.read(mapper.consent(client,principal),rows);}
}
''',encoding='utf-8')
p=root/'WebSecurityConfiguration.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('String publicUrl, JdbcTemplate jdbc,','String publicUrl, OAuthRepositoryMapper oauth,').replace('new JdbcRegisteredClientRepository(jdbc)','new MyBatisRegisteredClients(oauth)').replace('authorizationService(JdbcTemplate jdbc,','authorizationService(OAuthRepositoryMapper oauth,').replace('new JdbcOAuth2AuthorizationService(jdbc, clients)','new MyBatisAuthorizations(oauth,clients)').replace('consentService(JdbcTemplate jdbc,','consentService(OAuthRepositoryMapper oauth,').replace('new JdbcOAuth2AuthorizationConsentService(jdbc, clients)','new MyBatisAuthorizationConsents(oauth,clients)');p.write_text(s,encoding='utf-8')
