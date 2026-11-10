package com.appnexus.platform.authorization;

import java.util.UUID;

/** 权威权限变化的事务事件端口；实现必须参加当前数据库事务，不向外部消息系统先行发送。 */
public interface AuthorityEvents {
    /**
     * @param tenant 租户 UUID；平台变更为 null，由实现映射到明确平台事件边界
     * @param aggregate 角色、账号或成员不可变 UUID，不包含敏感原值
     */
    void invalidated(UUID tenant,UUID aggregate);
}
