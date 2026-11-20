package com.appnexus.platform.tenant;
import com.appnexus.platform.tenant.port.*;
/** 无框架邀请用例工厂，外部不导入internal。 */
public final class InvitationUseCases {
 private InvitationUseCases(){}
 /** @param platform 权威平台管理资格 @param records 固定邀请存取 @param transaction 完整邀请事务 @return 原邀请公开用例 */
 public static TenantInvitations create(PlatformAdministration platform,InvitationRecords records,InvitationTransaction transaction){return new com.appnexus.platform.tenant.internal.application.invitation.InvitationService(platform,records,transaction);}
}
