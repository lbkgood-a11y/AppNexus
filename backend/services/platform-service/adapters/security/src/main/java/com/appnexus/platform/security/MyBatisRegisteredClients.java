package com.appnexus.platform.security;
import com.appnexus.platform.persistence.identity.oauth.OAuthAuthorizationRepositoryMapper;
import org.springframework.security.oauth2.server.authorization.client.*;
/** MyBatis注册客户端仓储；JSON和参数映射复用框架公开映射器，密钥保持原哈希协议。 */
public final class MyBatisRegisteredClients implements RegisteredClientRepository {
 private final OAuthAuthorizationRepositoryMapper mapper;
 private final JdbcRegisteredClientRepository.RegisteredClientRowMapper rows=new JdbcRegisteredClientRepository.RegisteredClientRowMapper();
 private final JdbcRegisteredClientRepository.RegisteredClientParametersMapper parameters=new JdbcRegisteredClientRepository.RegisteredClientParametersMapper();
 public MyBatisRegisteredClients(OAuthAuthorizationRepositoryMapper mapper){this.mapper=mapper;}
 public void save(RegisteredClient client){mapper.saveClient(OAuthRowProjection.values(parameters.apply(client)));}
 public RegisteredClient findById(String id){return OAuthRowProjection.read(mapper.clientById(id),rows);}
 public RegisteredClient findByClientId(String id){return OAuthRowProjection.read(mapper.clientByClientId(id),rows);}
}
