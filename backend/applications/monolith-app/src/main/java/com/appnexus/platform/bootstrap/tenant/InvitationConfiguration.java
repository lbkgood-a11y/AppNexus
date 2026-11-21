package com.appnexus.platform.bootstrap.tenant;
import com.appnexus.platform.tenant.*;
import com.appnexus.platform.tenant.port.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.FieldProtection;
import org.springframework.context.annotation.*;
/** 邀请纯用例装配，不承担成员或权限决策。 */
@Configuration(proxyBeanMethods=false)
public class InvitationConfiguration {
 /** @param platform 权威平台资格 @param records 固定存取 @param transaction 完整事务
  * @param fields 统一实时字段保护 @return 邀请公开用例 */
 @Bean public TenantInvitations tenantInvitations(PlatformAdministration platform,InvitationRecords records,
         InvitationTransaction transaction,FieldProtection fields){
  return InvitationUseCases.create(platform,records,transaction,actor->fields
      .restrictions(AccessAuthorization.Subject.platform(actor),"tenant.invitation").containsKey("accountId"));
 }
}
