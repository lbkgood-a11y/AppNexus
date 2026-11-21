package com.appnexus.platform.tenant.port;

import java.util.UUID;

/** 平台邀请用例的统一字段策略端口；受邀账号隐藏时也不能从发起邀请的请求回传。 */
@FunctionalInterface
public interface InvitationFieldAccess {
    /** @param actor 已复核的平台账号 @return 当前账号是否必须隐藏受邀账号标识 */
    boolean hideInvitee(UUID actor);
}
