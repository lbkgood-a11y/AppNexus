package com.appnexus.platform.bootstrap.partner;
import com.appnexus.platform.configuration.PartnerDictionaryQueries;
import com.appnexus.platform.configuration.port.PartnerDictionaryRecords;
import org.springframework.context.annotation.*;
/** 合作方字典查询装配，不承担业务准入或 SQL。 */
@Configuration(proxyBeanMethods=false)
public class PartnerDictionaryConfiguration {
 /** @param records 固定持久化输出端口 @return 公开查询用例 */
 @Bean public PartnerDictionaryQueries partnerDictionaryQueries(PartnerDictionaryRecords records){return PartnerDictionaryQueries.create(records);}
 /**
  * 在引用事务锁定客户端授权版本后，按绑定租户和当前注册权限签发统一 SQL 许可。
  * @param records 引用固定存取端口
  * @param transaction 同连接事务端口
  * @param clients 机密客户端的权威策略来源
  * @param registration 已注册的 OAuth 客户端与授权范围
  * @param writes 绑定租户、操作和客户端的事务许可门禁
  * @return 合作方引用公开用例
  */
 @Bean public com.appnexus.platform.configuration.PartnerReferences partnerReferences(
     com.appnexus.platform.configuration.port.PartnerReferenceRepository records,
     com.appnexus.platform.configuration.port.PartnerReferenceTransaction transaction,
     com.appnexus.platform.security.IntegrationClients clients,
     org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository registration,
     com.appnexus.platform.authorization.ScopedWriteGate writes){
  return com.appnexus.platform.configuration.PartnerReferences.create(records,(client,tenant)->{
   String scope=com.appnexus.platform.configuration.PartnerReferences.REQUIRED_SCOPE;
   try{
    var tenantId=java.util.UUID.fromString(tenant);
    writes.authorizeServiceOperation(tenantId,scope,java.util.List.of(client),()->{
     var policy=clients.policy(client);
     var registered=registration.findById(client);
     return policy!=null&&policy.enabled()&&tenantId.equals(policy.tenant())
         &&registered!=null&&registered.getScopes().contains(scope);
    });
    return true;
   }catch(SecurityException denied){
    return false;
   }
  },transaction);
 }
}
