package com.appnexus.platform.tenant;

import java.util.*;

/** 平台用户与租户开通端口；每个操作独立验证平台管理员，不允许平台身份绕过租户业务隔离。 */
public interface PlatformAdministration {
    /** 平台账号摘要，不含认证凭据。 */
    record User(UUID id, String username, String displayName, boolean enabled) {}
    /** 租户目录，不包含租户业务内容。 */
    record Tenant(UUID id, String name, boolean enabled) {}
    /** 成员是账号与租户的关联，不等价于组织归属或业务授权。 */
    record Member(UUID memberId, UUID accountId, String username, String displayName, boolean enabled) {}
    /** 带总数的目录页，页码从 1 开始，每页最多 100 条。 */
    record Page<T>(List<T> items, long total) {}
    /** @param actor 经过认证的全局账号 UUID，禁止使用客户端操作者字段 */
    boolean isAdministrator(UUID actor);
    /** @param page 从 1 开始；size 范围 1–100，query 最长 100 字符 */
    Page<User> users(UUID actor, int page, int size, String query);
    /** @param passwordHash 已校验至少 12 字符密码的 Argon2id 哈希，不接收明文 */
    User createUser(UUID actor, UUID id, String username, String displayName, String passwordHash);
    /** 停用立即阻止后续认证请求；不能停用自己或任何平台管理员。 */
    void setUserEnabled(UUID actor, UUID target, boolean enabled);
    /** @param page 从 1 开始；size 范围 1–100，query 最长 100 字符 */
    Page<Tenant> tenants(UUID actor, int page, int size, String query);
    /** 原子创建租户、首个管理员成员与组织能力；重试必须使用相同 requestId 和参数。 */
    Tenant createTenant(UUID actor, UUID requestId, String name, UUID administratorAccountId);
    /** 停用租户不删除成员或业务数据，恢复后原有授权仍按有效期判定。 */
    void setTenantEnabled(UUID actor, UUID tenant, boolean enabled);
    /** 分页读取指定租户成员，平台管理只暴露账号关联摘要。 */
    Page<Member> members(UUID actor, UUID tenant, int page, int size, String query);
    /** 新成员默认无组织能力；重复添加、已停用成员重新添加均拒绝，必须显式变更状态。 */
    void addMember(UUID actor, UUID tenant, UUID account);
    /** 停用时原子撤销该成员全部组织权限；恢复成员不会恢复历史授权。 */
    void setMemberEnabled(UUID actor, UUID tenant, UUID member, boolean enabled);
}
