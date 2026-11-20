package com.appnexus.platform.bootstrap.partner;
import com.appnexus.platform.configuration.PartnerDictionaryQueries;
import com.appnexus.platform.configuration.port.PartnerDictionaryRecords;
import org.springframework.context.annotation.*;
/** 合作方字典查询装配，不承担业务准入或 SQL。 */
@Configuration(proxyBeanMethods=false)
public class PartnerDictionaryConfiguration {
 /** @param records 固定持久化输出端口 @return 公开查询用例 */
 @Bean public PartnerDictionaryQueries partnerDictionaryQueries(PartnerDictionaryRecords records){return PartnerDictionaryQueries.create(records);}
 /** @param records 引用固定存取端口 @param transaction 同连接事务端口 @param clients 权威机密客户端技术入口 @return 合作方引用公开用例 */
 @Bean public com.appnexus.platform.configuration.PartnerReferences partnerReferences(com.appnexus.platform.configuration.port.PartnerReferenceRepository records,com.appnexus.platform.configuration.port.PartnerReferenceTransaction transaction,com.appnexus.platform.security.IntegrationClients clients){return com.appnexus.platform.configuration.PartnerReferences.create(records,client->{var policy=clients.policy(client);return policy!=null&&policy.enabled();},transaction);}
}
